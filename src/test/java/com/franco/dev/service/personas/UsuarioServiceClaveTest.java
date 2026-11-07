package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.personas.UsuarioGraphQL;
import com.franco.dev.graphql.personas.input.UsuarioInput;
import com.franco.dev.repository.personas.PersonaRepository;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import com.franco.dev.repository.personas.RoleRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.service.utils.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La clave de un usuario: en el alta, la elegida solo si se pide (respetarClave); en una edición
 * sin clave, la guardada. Antes un update sin clave la dejaba en null y el login fallaba (#477).
 */
class UsuarioServiceClaveTest {

    private static final LocalDateTime CREADO = LocalDateTime.of(2025, 3, 1, 8, 0);

    private UsuarioRepository repository;
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        repository = mock(UsuarioRepository.class);
        service = new UsuarioService(repository, mock(RoleRepository.class), mock(UsuarioRoleService.class),
                mock(RoleService.class), mock(PersonaRepository.class), mock(ImageService.class),
                mock(UsuarioEmbeddingCacheService.class), mock(EmbeddingGaleriaService.class));
        when(repository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Usuario guardado() {
        Usuario u = new Usuario();
        u.setId(7L);
        u.setNickname("JUAN");
        u.setPassword("SECRETA");
        u.setEmail("juan@frc.com.py");
        u.setCreadoEn(CREADO);
        when(repository.findById(7L)).thenReturn(Optional.of(u));
        return u;
    }

    private Usuario edicion(String clave) {
        Usuario u = new Usuario();
        u.setId(7L);
        u.setNickname("juan");
        u.setPassword(clave);
        return u;
    }

    private Usuario alta(String clave) {
        Usuario u = new Usuario();
        u.setNickname("nuevo");
        u.setPassword(clave);
        return u;
    }

    @Test
    void altaSinClaveQuedaConLaDeSiempre() {
        assertEquals("123", service.save(alta(null), true).getPassword());
        assertEquals("123", service.save(alta("  "), true).getPassword());
    }

    @Test
    void altaSinPedirloIgnoraLaClaveRecibida() {
        // /new y el alta interna de funcionario siguen dejando 123.
        assertEquals("123", service.save(alta("abc")).getPassword());
    }

    @Test
    void altaPidiendoloGuardaLaClaveElegida() {
        Usuario res = service.save(alta("abc"), true);
        assertEquals("ABC", res.getPassword());
        assertNotNull(res.getCreadoEn());
    }

    @Test
    void edicionSinClaveConservaLaGuardada() {
        guardado();
        assertEquals("SECRETA", service.save(edicion(null)).getPassword());
        assertEquals("SECRETA", service.save(edicion("")).getPassword());
        assertEquals("SECRETA", service.save(edicion("  "), true).getPassword());
    }

    @Test
    void edicionConClaveLaCambia() {
        guardado();
        assertEquals("NUEVA", service.save(edicion("nueva")).getPassword());
    }

    @Test
    void edicionConservaEmailYCreadoEn() {
        guardado();
        Usuario res = service.save(edicion("x"));
        assertEquals("juan@frc.com.py", res.getEmail());
        assertEquals(CREADO, res.getCreadoEn());
    }

    @Test
    void edicionDeUnIdInexistenteNoRevienta() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        Usuario res = service.save(edicion(null));
        assertNull(res.getPassword());
        assertEquals("JUAN", res.getNickname());
    }

    @Test
    void guardaLaEntidadRecibidaNoLaLeida() {
        Usuario leido = guardado();
        Usuario recibido = edicion(null);
        service.save(recibido);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertSame(recibido, captor.getValue());
        assertNotSame(leido, captor.getValue());
        assertEquals("SECRETA", leido.getPassword());
        assertEquals("JUAN", leido.getNickname());
    }

    @Test
    void saveUsuarioSinClaveNoLaDejaEnNull() {
        guardado();
        UsuarioGraphQL graphQL = new UsuarioGraphQL();
        ReflectionTestUtils.setField(graphQL, "service", service);
        ReflectionTestUtils.setField(graphQL, "seg", new TesoreriaSecurityFalso(1L, "ADMIN"));

        UsuarioInput input = new UsuarioInput();
        input.setId(7L);
        input.setNickname("juan");
        input.setActivo(true);
        input.setRespetarClave(true);

        assertEquals("SECRETA", graphQL.saveUsuario(input).getPassword());
    }

    @Test
    void saveUsuarioAltaConRespetarClave() {
        UsuarioGraphQL graphQL = new UsuarioGraphQL();
        ReflectionTestUtils.setField(graphQL, "service", service);
        ReflectionTestUtils.setField(graphQL, "seg", new TesoreriaSecurityFalso(1L, "ADMIN"));

        UsuarioInput input = new UsuarioInput();
        input.setNickname("nuevo");
        input.setPassword("abc");
        input.setActivo(true);
        input.setRespetarClave(true);
        assertEquals("ABC", graphQL.saveUsuario(input).getPassword());

        input.setRespetarClave(null);
        assertEquals("123", graphQL.saveUsuario(input).getPassword());
    }

    // --- Token de servicio (#579): el bot no se fija una clave propia ---------------------------

    private void sesionConTokenDeServicio(boolean deServicio) {
        java.util.List<org.springframework.security.core.GrantedAuthority> a = new java.util.ArrayList<>();
        if (deServicio) a.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                com.franco.dev.security.TokenServicioFormato.AUTORIDAD_SESION));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("JUAN", null, a));
    }

    private UsuarioInput cambioDeClavePropia() {
        UsuarioInput input = new UsuarioInput();
        input.setId(7L);
        input.setNickname("juan");
        input.setPassword("conocida");
        input.setActivo(true);
        input.setRespetarClave(true);
        return input;
    }

    @Test
    void conTokenDeServicioNoSeCambiaLaClavePropia() {
        guardado();
        UsuarioGraphQL graphQL = new UsuarioGraphQL();
        ReflectionTestUtils.setField(graphQL, "service", service);
        ReflectionTestUtils.setField(graphQL, "seg", new TesoreriaSecurityFalso(7L));
        try {
            sesionConTokenDeServicio(true);
            assertThrows(graphql.GraphQLException.class, () -> graphQL.saveUsuario(cambioDeClavePropia()));
            // Tampoco con EDITAR USUARIOS: ese rol no habilita a un bot a tocarse a si mismo.
            ReflectionTestUtils.setField(graphQL, "seg", new TesoreriaSecurityFalso(7L, "EDITAR USUARIOS"));
            assertThrows(graphql.GraphQLException.class, () -> graphQL.saveUsuario(cambioDeClavePropia()));
            verify(repository, never()).save(any(Usuario.class));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void conSesionComunCadaUnoSigueCambiandoSuClave() {
        guardado();
        UsuarioGraphQL graphQL = new UsuarioGraphQL();
        ReflectionTestUtils.setField(graphQL, "service", service);
        ReflectionTestUtils.setField(graphQL, "seg", new TesoreriaSecurityFalso(7L));
        try {
            sesionConTokenDeServicio(false);
            assertEquals("CONOCIDA", graphQL.saveUsuario(cambioDeClavePropia()).getPassword());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
