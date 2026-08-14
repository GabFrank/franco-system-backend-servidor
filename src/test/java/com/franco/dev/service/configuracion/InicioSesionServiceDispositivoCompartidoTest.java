package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.configuracion.InicioSesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El push de un aparato va al ultimo que inicio sesion en el, pero eso se
 * resuelve liberando el token y nunca cerrando la sesion de otra persona:
 * una sesion que se cierra sola es un efecto lateral que no queremos.
 */
class InicioSesionServiceDispositivoCompartidoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 8, 11, 15, 0);
    private static final String DISPOSITIVO = "bf36ac7f-302d-40e2-975b-82a1e4000000";

    private InicioSesionRepository repository;
    private InicioSesionService service;

    @BeforeEach
    void setUp() {
        repository = mock(InicioSesionRepository.class);
        service = new InicioSesionService(repository, mock(ApplicationEventPublisher.class));
    }

    private InicioSesion sesion(Long id, Long sucursalId, Long usuarioId, String token) {
        InicioSesion sesion = new InicioSesion();
        sesion.setId(id);
        sesion.setSucursalId(sucursalId);
        if (usuarioId != null) {
            Usuario usuario = new Usuario();
            usuario.setId(usuarioId);
            sesion.setUsuario(usuario);
        }
        sesion.setIdDispositivo(DISPOSITIVO);
        sesion.setToken(token);
        return sesion;
    }

    @Test
    void elUsuarioAnteriorDejaDeSerDestinoPeroNoSeLeCierraLaSesion() {
        InicioSesion anterior = sesion(10L, 0L, 361L, "token-viejo");
        InicioSesion nueva = sesion(20L, 0L, 402L, "token-nuevo");
        when(repository.findByIdDispositivoAndHoraFinIsNull(DISPOSITIVO))
                .thenReturn(Arrays.asList(anterior, nueva));

        service.liberarTokenDeOtrasSesionesDelDispositivo(nueva);

        assertNull(anterior.getToken(), "deja de recibir notificaciones en un aparato que ya no usa");
        assertNull(anterior.getHoraFin(), "pero su sesion NO se cierra: eso es del ciclo de login/logout");
    }

    @Test
    void laSesionNuevaConservaSuToken() {
        InicioSesion nueva = sesion(20L, 0L, 402L, "token-nuevo");
        when(repository.findByIdDispositivoAndHoraFinIsNull(DISPOSITIVO))
                .thenReturn(Collections.singletonList(nueva));

        service.liberarTokenDeOtrasSesionesDelDispositivo(nueva);

        assertNotNull(nueva.getToken());
        assertNull(nueva.getHoraFin());
    }

    @Test
    void lasSesionesSinTokenNiSeTocan() {
        InicioSesion sinToken = sesion(10L, 0L, 361L, null);
        InicioSesion nueva = sesion(20L, 0L, 402L, "token-nuevo");
        when(repository.findByIdDispositivoAndHoraFinIsNull(DISPOSITIVO))
                .thenReturn(Arrays.asList(sinToken, nueva));

        service.liberarTokenDeOtrasSesionesDelDispositivo(nueva);

        verify(repository, never()).save(any(InicioSesion.class));
    }

    @Test
    void mismoIdEnOtraSucursalNoEsLaMismaSesion() {
        InicioSesion otraSucursal = sesion(20L, 5L, 361L, "token-de-otra-sucursal");
        InicioSesion nueva = sesion(20L, 0L, 402L, "token-nuevo");
        when(repository.findByIdDispositivoAndHoraFinIsNull(DISPOSITIVO))
                .thenReturn(Arrays.asList(otraSucursal, nueva));

        service.liberarTokenDeOtrasSesionesDelDispositivo(nueva);

        assertNull(otraSucursal.getToken(),
                "el id de inicio_sesion se genera por sucursal: mismo id no significa misma sesion");
        assertNotNull(nueva.getToken());
    }

    @Test
    void sinIdDispositivoNoSeTocaNada() {
        InicioSesion nueva = sesion(20L, 0L, 402L, "token-nuevo");
        nueva.setIdDispositivo(null);

        service.liberarTokenDeOtrasSesionesDelDispositivo(nueva);

        verify(repository, never()).findByIdDispositivoAndHoraFinIsNull(anyString());
        verify(repository, never()).save(any(InicioSesion.class));
    }

    @Test
    void cerrarSesionesPreviasSigueSiendoSoloDelMismoUsuario() {
        InicioSesion previaPropia = sesion(10L, 0L, 402L, "token-viejo");
        InicioSesion nueva = sesion(20L, 0L, 402L, "token-nuevo");
        when(repository.findByUsuarioIdAndIdDispositivoAndHoraFinIsNull(402L, DISPOSITIVO))
                .thenReturn(Arrays.asList(previaPropia, nueva));

        service.cerrarOtrasSesionesActivasDelDispositivo(nueva, AHORA);

        assertEquals(AHORA, previaPropia.getHoraFin());
        verify(repository, never()).findByIdDispositivoAndHoraFinIsNull(anyString());
    }
}
