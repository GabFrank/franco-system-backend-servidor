package com.franco.dev.service.operaciones.compras;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Los roles salen de la base por el nickname de la sesión; el nickname ADMIN pasa sin consultar. */
class ComprasSecurityServiceTest {

    private UsuarioService usuarios;
    private RoleService roles;
    private ComprasSecurityService seg;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioService.class);
        roles = mock(RoleService.class);
        seg = new ComprasSecurityService();
        ReflectionTestUtils.setField(seg, "usuarioService", usuarios);
        ReflectionTestUtils.setField(seg, "roleService", roles);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void sesion(String nick) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(nick, null, Collections.emptyList()));
    }

    private static Role rol(String nombre) {
        Role r = new Role();
        r.setNombre(nombre);
        return r;
    }

    private void usuarioConRoles(String nick, String... nombres) {
        Usuario u = new Usuario();
        u.setId(7L);
        when(usuarios.findByNickname(nick)).thenReturn(Optional.of(u));
        when(roles.findByUsuarioId(7L)).thenReturn(Arrays.stream(nombres).map(ComprasSecurityServiceTest::rol).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void nickAdminPasaSinConsultar() {
        sesion("admin");
        seg.exigirAdmin();
        seg.exigirCompras();
        verifyNoInteractions(usuarios, roles);
    }

    @Test
    void rolDeComprasConEspaciosYMinusculas() {
        sesion("juan");
        usuarioConRoles("juan", " recibir pedidos ");
        seg.exigirCompras();
        assertThrows(GraphQLException.class, seg::exigirAdmin);
    }

    @Test
    void rolAdminEnLaBase() {
        sesion("ana");
        usuarioConRoles("ana", "ADMIN");
        seg.exigirAdmin();
        seg.exigirCompras();
    }

    @Test
    void sinRolesOUsuarioInexistenteSeRechaza() {
        sesion("pedro");
        usuarioConRoles("pedro", "VENTA TOUCH");
        assertThrows(GraphQLException.class, seg::exigirCompras);
        sesion("nadie");
        when(usuarios.findByNickname("nadie")).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, seg::exigirCompras);
        SecurityContextHolder.clearContext();
        assertThrows(GraphQLException.class, seg::exigirAdmin);
    }
}
