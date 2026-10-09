package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Issue #346: la persona del usuario autenticado es lo que vincula el login con el
 * funcionario (usuario.persona_id == funcionario.persona_id).
 */
class RrhhSecurityServiceTest {

    private static final Long PERSONA_ID = 987L;

    @Mock private UsuarioService usuarioService;
    @Mock private RoleService roleService;

    @InjectMocks private RrhhSecurityService seg;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    private Usuario autenticar(String nickname) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nickname, "x", Collections.emptyList()));
        Usuario u = new Usuario();
        when(usuarioService.findByNickname(nickname)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void la_persona_del_usuario_autenticado() {
        Persona p = new Persona();
        p.setId(PERSONA_ID);
        autenticar("JPEREZ").setPersona(p);

        assertEquals(PERSONA_ID, seg.currentPersonaId());
    }

    @Test
    void un_usuario_sin_persona_no_tiene_persona() {
        autenticar("SINPERSONA");

        assertNull(seg.currentPersonaId());
    }

    @Test
    void sin_sesion_no_hay_persona() {
        assertNull(seg.currentPersonaId());
    }
}
