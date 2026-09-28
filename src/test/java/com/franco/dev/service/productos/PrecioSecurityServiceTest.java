package com.franco.dev.service.productos;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PrecioSecurityServiceTest {

    @Mock private UsuarioService usuarioService;
    @Mock private RoleService roleService;
    @InjectMocks private PrecioSecurityService seg;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() { mocks = MockitoAnnotations.openMocks(this); }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    private void autenticar(String nickname, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nickname, null, Collections.emptyList()));
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        when(usuarioService.findByNickname(nickname)).thenReturn(Optional.of(usuario));
        List<Role> lista = Arrays.stream(roles).map(n -> { Role r = new Role(); r.setNombre(n); return r; })
                .collect(Collectors.toList());
        when(roleService.findByUsuarioId(7L)).thenReturn(lista);
    }

    @Test
    void conCrearPreciosPasa() {
        autenticar("ana", "CREAR PRECIOS");
        assertDoesNotThrow(() -> seg.requireGestionar());
    }

    @Test
    void conEditarPreciosEnMinusculaPasa() {
        autenticar("ana", "editar precios ");
        assertDoesNotThrow(() -> seg.requireGestionar());
    }

    @Test
    void adminPasaSinRolesDePrecio() {
        autenticar("ana", "ADMIN");
        assertDoesNotThrow(() -> seg.requireGestionar());
    }

    @Test
    void sinRolDePrecioSeRechaza() {
        autenticar("ana", "VENTA TOUCH");
        GraphQLException e = assertThrows(GraphQLException.class, () -> seg.requireGestionar());
        assertTrue(e.getMessage().contains("CREAR PRECIOS"));
    }

    @Test
    void sinAutenticacionSeRechaza() {
        assertThrows(GraphQLException.class, () -> seg.requireGestionar());
    }
}
