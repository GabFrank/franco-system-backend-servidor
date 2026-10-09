package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.VentaGraphQL;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.sifen.SifenEventoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Cancelar (o reactivar) una venta y cancelar una factura exigen CANCELACION DE VENTA tambien en el
 * central (issue #340): antes el rol solo lo miraba el PDV del desktop, y el backoffice ninguno. Se
 * arma con el {@link TesoreriaSecurityService} real para probar el nombre del rol de punta a punta.
 */
class CancelarVentaYFacturaRolTest {

    private static final Long USUARIO_ID = 7L;
    private static final Long VENTA_ID = 40L;
    private static final Long FACTURA_ID = 300L;
    private static final Long SUCURSAL_ID = 1L;

    private VentaService ventaService;
    private FacturaLegalService facturaLegalService;
    private SifenEventoService sifenEventoService;
    private DocumentoElectronicoService documentoElectronicoService;
    private UsuarioService usuarioService;
    private RoleService roleService;
    private VentaCreditoService ventaCreditoService;
    private VentaGraphQL ventaGraphQL;
    private VentaCreditoGraphQL ventaCreditoGraphQL;
    private FacturaLegalGraphQL facturaLegalGraphQL;

    @BeforeEach
    void setUp() {
        ventaService = mock(VentaService.class);
        facturaLegalService = mock(FacturaLegalService.class);
        sifenEventoService = mock(SifenEventoService.class);
        documentoElectronicoService = mock(DocumentoElectronicoService.class);
        usuarioService = mock(UsuarioService.class);
        roleService = mock(RoleService.class);

        TesoreriaSecurityService seg = new TesoreriaSecurityService();
        ReflectionTestUtils.setField(seg, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(seg, "roleService", roleService);

        ventaGraphQL = new VentaGraphQL();
        ReflectionTestUtils.setField(ventaGraphQL, "service", ventaService);
        ReflectionTestUtils.setField(ventaGraphQL, "seg", seg);

        ventaCreditoService = mock(VentaCreditoService.class);
        ventaCreditoGraphQL = new VentaCreditoGraphQL();
        ReflectionTestUtils.setField(ventaCreditoGraphQL, "service", ventaCreditoService);
        ReflectionTestUtils.setField(ventaCreditoGraphQL, "seg", seg);

        facturaLegalGraphQL = new FacturaLegalGraphQL();
        ReflectionTestUtils.setField(facturaLegalGraphQL, "service", facturaLegalService);
        ReflectionTestUtils.setField(facturaLegalGraphQL, "ventaService", ventaService);
        ReflectionTestUtils.setField(facturaLegalGraphQL, "sifenEventoService", sifenEventoService);
        ReflectionTestUtils.setField(facturaLegalGraphQL, "documentoElectronicoService", documentoElectronicoService);
        ReflectionTestUtils.setField(facturaLegalGraphQL, "seg", seg);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String nickname, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nickname, null, Collections.emptyList()));
        Usuario autenticado = new Usuario();
        autenticado.setId(USUARIO_ID);
        when(usuarioService.findByNickname(nickname)).thenReturn(Optional.of(autenticado));
        List<Role> lista = Arrays.stream(roles).map(n -> { Role r = new Role(); r.setNombre(n); return r; })
                .collect(Collectors.toList());
        when(roleService.findByUsuarioId(USUARIO_ID)).thenReturn(lista);
    }

    private Venta venta(VentaEstado estado) {
        Venta venta = new Venta();
        venta.setId(VENTA_ID);
        venta.setSucursalId(SUCURSAL_ID);
        venta.setEstado(estado);
        when(ventaService.findByIdAndSucursalId(VENTA_ID, SUCURSAL_ID)).thenReturn(venta);
        when(ventaService.cancelarVenta(venta)).thenReturn(true);
        return venta;
    }

    private FacturaLegal facturaEnPapel() {
        FacturaLegal factura = new FacturaLegal();
        factura.setId(FACTURA_ID);
        factura.setSucursalId(SUCURSAL_ID);
        factura.setActivo(true);
        when(facturaLegalService.findByIdAndSucursalId(FACTURA_ID, SUCURSAL_ID)).thenReturn(factura);
        return factura;
    }

    @Test
    @DisplayName("sin el rol no se cancela la venta ni se llega a leerla")
    void sinRolNoCancelaLaVenta() {
        autenticar("ana", "VENTA TOUCH", "ANALISIS DE CAJA", "ANALISIS DE VENTA");
        venta(VentaEstado.CONCLUIDA);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> ventaGraphQL.cancelarVenta(VENTA_ID, SUCURSAL_ID));

        assertTrue(e.getMessage().contains("CANCELACION DE VENTA"));
        verifyNoInteractions(ventaService);
    }

    @Test
    @DisplayName("sin el rol tampoco se reactiva una venta cancelada: es la misma mutation")
    void sinRolNoReactivaLaVenta() {
        autenticar("ana", "ANALISIS DE CAJA");
        Venta cancelada = venta(VentaEstado.CANCELADA);

        assertThrows(GraphQLException.class, () -> ventaGraphQL.cancelarVenta(VENTA_ID, SUCURSAL_ID));

        assertEquals(VentaEstado.CANCELADA, cancelada.getEstado());
        verifyNoInteractions(ventaService);
    }

    @Test
    @DisplayName("sin sesion no se cancela la venta")
    void sinAutenticacionNoCancelaLaVenta() {
        assertThrows(GraphQLException.class, () -> ventaGraphQL.cancelarVenta(VENTA_ID, SUCURSAL_ID));
        verifyNoInteractions(ventaService);
    }

    @Test
    @DisplayName("sin el rol la factura no se cancela: sale como error, no como un string, y no se llama a SIFEN")
    void sinRolNoCancelaLaFactura() {
        autenticar("ana", "ANALISIS DE CAJA");
        FacturaLegal factura = facturaEnPapel();

        assertThrows(GraphQLException.class,
                () -> facturaLegalGraphQL.cancelarFacturaLegal(FACTURA_ID, SUCURSAL_ID, true));

        assertTrue(factura.getActivo());
        verifyNoInteractions(facturaLegalService, ventaService, sifenEventoService, documentoElectronicoService);
    }

    @Test
    @DisplayName("sin el rol tampoco se cancela por la venta a credito, que alterna el estado de la venta")
    void sinRolNoCancelaPorLaVentaACredito() {
        autenticar("ana", "ANALISIS DE CAJA");

        assertThrows(GraphQLException.class, () -> ventaCreditoGraphQL.cancelarVentaCredito(9L, SUCURSAL_ID));

        verifyNoInteractions(ventaCreditoService);
    }

    @Test
    @DisplayName("con el rol la venta a credito se cancela")
    void conRolCancelaLaVentaACredito() {
        autenticar("ana", "CANCELACION DE VENTA");
        when(ventaCreditoService.cancelarVentaCredito(9L, SUCURSAL_ID, null)).thenReturn(true);

        assertTrue(ventaCreditoGraphQL.cancelarVentaCredito(9L, SUCURSAL_ID));
    }

    @Test
    @DisplayName("con CANCELACION DE VENTA se cancela la venta y la factura")
    void conRolCancela() {
        autenticar("ana", "cancelacion de venta ");
        Venta venta = venta(VentaEstado.CONCLUIDA);
        FacturaLegal factura = facturaEnPapel();

        assertTrue(ventaGraphQL.cancelarVenta(VENTA_ID, SUCURSAL_ID));
        String resultado = facturaLegalGraphQL.cancelarFacturaLegal(FACTURA_ID, SUCURSAL_ID, false);

        verify(ventaService, times(1)).cancelarVenta(venta);
        assertTrue(resultado.startsWith("EXITO"), resultado);
        assertFalse(factura.getActivo());
        verify(facturaLegalService, times(1)).save(factura);
    }

    @Test
    @DisplayName("ADMIN cancela sin tener el rol")
    void adminCancela() {
        autenticar("ana", "ADMIN");
        Venta venta = venta(VentaEstado.CONCLUIDA);
        facturaEnPapel();

        assertTrue(ventaGraphQL.cancelarVenta(VENTA_ID, SUCURSAL_ID));
        assertTrue(facturaLegalGraphQL.cancelarFacturaLegal(FACTURA_ID, SUCURSAL_ID, false).startsWith("EXITO"));

        verify(ventaService, times(1)).cancelarVenta(venta);
    }
}
