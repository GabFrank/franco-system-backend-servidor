package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.enums.OrigenNotaRemision;
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
        assertThrows(GraphQLException.class, () -> seg.requireEmitir());
    }

    @Test
    void conElRolDeEmitir_deja() {
        autenticar("cajera", "FACTURACION EMITIR");

        seg.requireEmitir();
        seg.requireVer();   // quien emite tambien ve
    }

    @Test
    void conSoloElRolDeVer_noDejaEmitir() {
        autenticar("auditor", "FACTURACION VER");

        seg.requireVer();

        assertThrows(GraphQLException.class, () -> seg.requireEmitir());
    }

    @Test
    void sinNingunRolDeFacturacion_noDejaNada() {
        autenticar("cajera", "RRHH VER");

        assertThrows(GraphQLException.class, () -> seg.requireVer());
        assertThrows(GraphQLException.class, () -> seg.requireEmitir());
    }

    @Test
    void elRolSeComparaSinEspaciosNiMayusculas() {
        autenticar("cajera", "  facturacion emitir ");

        seg.requireEmitir();
    }

    @Test
    void elRolAdminEsBypass() {
        autenticar("supervisor", "ADMIN");

        seg.requireEmitir();
        seg.requireVer();
    }

    @Test
    void elNicknameAdminEsBypassAunqueNoTengaRoles() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("ADMIN", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        seg.requireEmitir();
    }

    @Test
    void verLoHabilitaCualquierRolDeFacturacion() {
        autenticar("auditor", "FACTURACION EMITIR");

        seg.requireVer();
    }

    @Test
    void elRolDeRemisionVeYEmiteLaNotaDeUnaTransferencia() {
        autenticar("deposito", "NOTA REMISION EMITIR");

        seg.requireVerRemisionDeTransferencia();
        seg.requireEmitirAlgunaRemision();
        seg.requireEmitirRemision(OrigenNotaRemision.TRANSFERENCIA);
        assertTrue(seg.emiteSoloDesdeTransferencia());
        assertTrue(seg.veSoloNotasDeTransferencia());
    }

    @Test
    void elRolDeRemisionNoEmiteOtrosOrigenes() {
        autenticar("deposito", "NOTA REMISION EMITIR");

        assertThrows(GraphQLException.class, () -> seg.requireEmitirRemision(OrigenNotaRemision.MANUAL));
        assertThrows(GraphQLException.class, () -> seg.requireEmitirRemision(OrigenNotaRemision.FACTURA));
        assertThrows(GraphQLException.class, () -> seg.requireEmitirRemision(null));
    }

    @Test
    void elRolDeRemisionNoAbreElRestoDeFacturacion() {
        autenticar("deposito", "NOTA REMISION EMITIR");

        // requireVer y requireEmitir son los del listado, el reenvío, la anulación y las notas de crédito
        assertThrows(GraphQLException.class, () -> seg.requireVer());
        assertThrows(GraphQLException.class, () -> seg.requireEmitir());
    }

    @Test
    void elRolDeEmitirSigueEmitiendoCualquierOrigenYNoQuedaAcotado() {
        autenticar("facturador", "FACTURACION EMITIR", "NOTA REMISION EMITIR");

        seg.requireEmitirRemision(OrigenNotaRemision.MANUAL);
        seg.requireEmitirRemision(OrigenNotaRemision.TRANSFERENCIA);
        assertFalse(seg.emiteSoloDesdeTransferencia());
        assertFalse(seg.veSoloNotasDeTransferencia());
    }

    @Test
    void conSoloElRolDeVer_veLaNotaDeUnaTransferenciaPeroNoLaEmite() {
        autenticar("auditor", "FACTURACION VER");

        seg.requireVerRemisionDeTransferencia();
        assertFalse(seg.veSoloNotasDeTransferencia());
        assertThrows(GraphQLException.class, () -> seg.requireEmitirAlgunaRemision());
        assertThrows(GraphQLException.class, () -> seg.requireEmitirRemision(OrigenNotaRemision.TRANSFERENCIA));
    }

    @Test
    void sinRoles_noPasaNingunaPuertaDeRemision() {
        autenticar("cajero", "VER TRANSFERENCIA");

        assertThrows(GraphQLException.class, () -> seg.requireVerRemisionDeTransferencia());
        assertThrows(GraphQLException.class, () -> seg.requireEmitirAlgunaRemision());
        assertFalse(seg.emiteSoloDesdeTransferencia());
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
