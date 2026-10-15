package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.personas.UsuarioGraphQL;
import com.franco.dev.graphql.personas.input.UsuarioInput;
import com.franco.dev.repository.personas.PersonaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

        UsuarioInput input = new UsuarioInput();
        input.setNickname("nuevo");
        input.setPassword("abc");
        input.setActivo(true);
        input.setRespetarClave(true);
        assertEquals("ABC", graphQL.saveUsuario(input).getPassword());

        input.setRespetarClave(null);
        assertEquals("123", graphQL.saveUsuario(input).getPassword());
    }
}
