package com.franco.dev.service.financiero;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Los roles de facturacion se leen de la DB por el nickname del SecurityContext, no del JWT
 * (issue #177). Bypass de superusuario por rol ADMIN o por nickname ADMIN.
 */
class FacturacionSecurityServiceTest {

    @Mock private UsuarioService usuarioService;
    @Mock private RoleService roleService;

    @InjectMocks private FacturacionSecurityService seg;

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

    @Test
    void sinUsuarioAutenticado_rechaza() {
        assertThrows(GraphQLException.class, () -> seg.requireVer());
        assertThrows(GraphQLException.class, () -> seg.requireEmitirNr());
    }

    @Test
    void conElRolExacto_deja() {
        autenticar("cajera", "FACTURACION NR EMITIR");

        seg.requireEmitirNr();
        seg.requireVer();

        assertThrows(GraphQLException.class, () -> seg.requireEmitirNc());
        assertThrows(GraphQLException.class, () -> seg.requireAnular());
    }

    @Test
    void elRolSeComparaSinEspaciosNiMayusculas() {
        autenticar("cajera", "  facturacion nc emitir ");

        seg.requireEmitirNc();
    }

    @Test
    void elRolAdminEsBypass() {
        autenticar("supervisor", "ADMIN");

        seg.requireEmitirNr();
        seg.requireEmitirNc();
        seg.requireAnular();
        seg.requireVer();
    }

    @Test
    void elNicknameAdminEsBypassAunqueNoTengaRoles() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("ADMIN", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        seg.requireAnular();
    }

    @Test
    void verLoHabilitaCualquierRolDeFacturacion() {
        autenticar("auditor", "FACTURACION ANULAR");

        seg.requireVer();
    }

    private void autenticar(String nickname, String... roles) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(nickname, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Usuario usuario = new Usuario();
        usuario.setId(7L);
        when(usuarioService.findByNickname(nickname)).thenReturn(Optional.of(usuario));

        List<Role> lista = Arrays.stream(roles).map(nombre -> {
            Role role = new Role();
            role.setNombre(nombre);
            return role;
        }).collect(java.util.stream.Collectors.toList());
        when(roleService.findByUsuarioId(7L)).thenReturn(lista);
    }
}
