package com.franco.dev.service.operaciones;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El control de stock negativo lo ve quien tiene VER INVENTARIO o es superusuario. */
class InventarioSecurityServiceTest {

    private UsuarioService usuarioService;
    private RoleService roleService;
    private InventarioSecurityService seg;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        roleService = mock(RoleService.class);
        seg = new InventarioSecurityService();
        ReflectionTestUtils.setField(seg, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(seg, "roleService", roleService);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticado(String nickname, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nickname, "x", Collections.emptyList()));
        Usuario u = new Usuario();
        u.setId(601L);
        when(usuarioService.findByNickname(nickname)).thenReturn(Optional.of(u));
        java.util.List<Role> lista = new java.util.ArrayList<>();
        for (String nombre : roles) {
            Role r = new Role();
            r.setNombre(nombre);
            lista.add(r);
        }
        when(roleService.findByUsuarioId(601L)).thenReturn(lista);
    }

    @Test
    void conElRolPasa() {
        autenticado("PRUEBANR", "VER INVENTARIO");
        assertDoesNotThrow(() -> seg.requireVerInventario());
    }

    @Test
    void elRolAdminPasa() {
        autenticado("PRUEBANR", "ADMIN");
        assertDoesNotThrow(() -> seg.requireVerInventario());
    }

    @Test
    void sinElRolNoPasa() {
        autenticado("PRUEBANR", "VER MOVIMIENTO DE STOCK");
        assertThrows(GraphQLException.class, () -> seg.requireVerInventario());
    }

    @Test
    void sinSesionNoPasa() {
        assertThrows(GraphQLException.class, () -> seg.requireVerInventario());
    }
}
