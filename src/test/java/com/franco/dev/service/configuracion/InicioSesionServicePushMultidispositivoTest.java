package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.configuracion.InicioSesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Un usuario con varios dispositivos activos debe recibir la push en todos.
 * La deduplicacion existe para no mandar dos veces al mismo destino, no para
 * dejar un solo dispositivo por usuario.
 */
class InicioSesionServicePushMultidispositivoTest {

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

    private List<String> tokensDe(List<InicioSesion> sesiones) {
        return sesiones.stream().map(InicioSesion::getToken).collect(Collectors.toList());
    }

    @Test
    void enviaATodosLosDispositivosDelUsuario() {
        List<Long> usuarioIds = Collections.singletonList(1L);
        when(repository.findByUsuarioIdInWithValidTokens(usuarioIds)).thenReturn(Arrays.asList(
                sesion(30L, 1L, "celular", "token-celular"),
                sesion(20L, 1L, "tablet", "token-tablet"),
                sesion(10L, 1L, "desktop", "token-desktop")));

        List<InicioSesion> destinos = service.findSessionsWithValidTokensByUsuarioIds(usuarioIds);

        assertEquals(3, destinos.size(), "un usuario con 3 dispositivos debe tener 3 destinos push");
        assertTrue(tokensDe(destinos).containsAll(
                Arrays.asList("token-celular", "token-tablet", "token-desktop")));
    }

    @Test
    void noDuplicaElMismoDispositivoYSeQuedaConLaSesionMasReciente() {
        List<Long> usuarioIds = Collections.singletonList(1L);
        when(repository.findByUsuarioIdInWithValidTokens(usuarioIds)).thenReturn(Arrays.asList(
                sesion(50L, 1L, "celular", "token-nuevo"),
                sesion(40L, 1L, "celular", "token-viejo"),
                sesion(30L, 1L, "tablet", "token-tablet")));

        List<InicioSesion> destinos = service.findSessionsWithValidTokensByUsuarioIds(usuarioIds);

        assertEquals(2, destinos.size(), "un dispositivo repetido no debe generar dos envios");
        assertTrue(tokensDe(destinos).contains("token-nuevo"), "debe quedar la sesion mas reciente");
        assertTrue(tokensDe(destinos).contains("token-tablet"));
    }

    @Test
    void separaDispositivosDeUsuariosDistintos() {
        List<Long> usuarioIds = Arrays.asList(1L, 2L);
        when(repository.findByUsuarioIdInWithValidTokens(usuarioIds)).thenReturn(Arrays.asList(
                sesion(30L, 1L, "celular", "token-u1-celular"),
                sesion(20L, 1L, "tablet", "token-u1-tablet"),
                sesion(10L, 2L, "celular", "token-u2-celular")));

        List<InicioSesion> destinos = service.findSessionsWithValidTokensByUsuarioIds(usuarioIds);

        assertEquals(3, destinos.size());
    }

    @Test
    void sesionesSinDispositivoNoSeColapsanEntreSi() {
        List<Long> usuarioIds = Collections.singletonList(1L);
        when(repository.findByUsuarioIdInWithValidTokens(usuarioIds)).thenReturn(Arrays.asList(
                sesion(30L, 1L, null, "token-a"),
                sesion(20L, 1L, null, "token-b")));

        List<InicioSesion> destinos = service.findSessionsWithValidTokensByUsuarioIds(usuarioIds);

        assertEquals(2, destinos.size(), "sin id_dispositivo el token distingue el destino");
    }

    @Test
    void deduplicaTokensHuerfanosSinUsuario() {
        List<Long> usuarioIds = Collections.singletonList(1L);
        when(repository.findByUsuarioIdInWithValidTokens(usuarioIds)).thenReturn(Arrays.asList(
                sesion(30L, null, "celular", "token-huerfano"),
                sesion(20L, null, "celular", "token-huerfano")));

        List<InicioSesion> destinos = service.findSessionsWithValidTokensByUsuarioIds(usuarioIds);

        assertEquals(1, destinos.size(), "el mismo token sin usuario no debe enviarse dos veces");
    }
}
