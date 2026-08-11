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
 * Un dispositivo pertenece al ultimo que inicio sesion en el. Si queda abierta
 * la sesion de quien lo uso antes, esa persona sigue recibiendo notificaciones
 * en un aparato que ya no es suyo.
 */
class InicioSesionServiceDispositivoCompartidoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 8, 11, 15, 0);

    private InicioSesionRepository repository;
    private InicioSesionService service;

    @BeforeEach
    void setUp() {
        repository = mock(InicioSesionRepository.class);
        service = new InicioSesionService(repository, mock(ApplicationEventPublisher.class));
    }

    private InicioSesion sesion(Long id, Long usuarioId, String idDispositivo, String token) {
        InicioSesion sesion = new InicioSesion();
        sesion.setId(id);
        if (usuarioId != null) {
            Usuario usuario = new Usuario();
            usuario.setId(usuarioId);
            sesion.setUsuario(usuario);
        }
        sesion.setIdDispositivo(idDispositivo);
        sesion.setToken(token);
        return sesion;
    }

    @Test
    void alIniciarSesionSeCierraLaDelUsuarioAnteriorEnEseDispositivo() {
        InicioSesion anterior = sesion(10L, 361L, "caja-1", "token-viejo");
        InicioSesion nueva = sesion(20L, 402L, "caja-1", "token-nuevo");
        when(repository.findByIdDispositivoAndHoraFinIsNull("caja-1"))
                .thenReturn(Arrays.asList(anterior, nueva));

        service.cerrarOtrasSesionesActivasDelDispositivo(nueva, AHORA);

        assertEquals(AHORA, anterior.getHoraFin(), "la sesion del usuario anterior debe cerrarse");
        assertNull(anterior.getToken(), "y perder el token, o sigue recibiendo notificaciones ajenas");
    }

    @Test
    void laSesionNuevaNoSeCierraASiMisma() {
        InicioSesion nueva = sesion(20L, 402L, "caja-1", "token-nuevo");
        when(repository.findByIdDispositivoAndHoraFinIsNull("caja-1"))
                .thenReturn(Collections.singletonList(nueva));

        service.cerrarOtrasSesionesActivasDelDispositivo(nueva, AHORA);

        assertNull(nueva.getHoraFin());
        assertNotNull(nueva.getToken());
    }

    @Test
    void siguenCerrandoseLasSesionesPreviasDelMismoUsuario() {
        InicioSesion previaPropia = sesion(10L, 402L, "caja-1", "token-viejo");
        InicioSesion nueva = sesion(20L, 402L, "caja-1", "token-nuevo");
        when(repository.findByIdDispositivoAndHoraFinIsNull("caja-1"))
                .thenReturn(Arrays.asList(previaPropia, nueva));

        service.cerrarOtrasSesionesActivasDelDispositivo(nueva, AHORA);

        assertEquals(AHORA, previaPropia.getHoraFin());
        assertNull(previaPropia.getToken());
    }

    @Test
    void noSeTocanLasSesionesDeOtrosDispositivos() {
        InicioSesion nueva = sesion(20L, 402L, "caja-1", "token-nuevo");
        when(repository.findByIdDispositivoAndHoraFinIsNull("caja-1"))
                .thenReturn(Collections.singletonList(nueva));

        service.cerrarOtrasSesionesActivasDelDispositivo(nueva, AHORA);

        verify(repository, never()).findByUsuarioIdAndIdDispositivoAndHoraFinIsNull(any(), anyString());
    }

    @Test
    void sinIdDispositivoNoSeCierraNada() {
        InicioSesion nueva = sesion(20L, 402L, null, "token-nuevo");

        service.cerrarOtrasSesionesActivasDelDispositivo(nueva, AHORA);

        verify(repository, never()).findByIdDispositivoAndHoraFinIsNull(anyString());
        verify(repository, never()).save(any(InicioSesion.class));
    }
}
