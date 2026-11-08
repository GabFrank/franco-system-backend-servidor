package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reimprimir una factura o un balance: quien la emitió, el dueño de la caja o ANALISIS DE CAJA (ADMIN pasa). */
class ReimpresionAccesoTest {

    private static final String ROL = TesoreriaSecurityService.ANALISIS_DE_CAJA;
    private static final Long OTRO = 5L;

    private final RolCajaSoporte s = new RolCajaSoporte();

    @AfterEach
    void tearDown() {
        RolCajaSoporte.limpiar();
    }

    private static Usuario usuario(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        return u;
    }

    private static PdvCaja cajaDe(Long usuarioId) {
        PdvCaja c = new PdvCaja();
        c.setId(1185L);
        c.setUsuario(usuario(usuarioId));
        return c;
    }

    @Test
    void elDuenioPasaSinRol() {
        s.autenticar("cajera");
        s.seg.requireUsuarioORol(Arrays.asList(null, RolCajaSoporte.USUARIO_ID), ROL);
    }

    @Test
    void unAjenoSinRolSeRechaza() {
        s.autenticar("cajera");
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> s.seg.requireUsuarioORol(Collections.singletonList(OTRO), ROL));
        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        assertThrows(GraphQLException.class, () -> s.seg.requireUsuarioORol(Collections.emptyList(), ROL));
    }

    @Test
    void unAjenoConElRolOAdminPasa() {
        s.autenticar("analista", ROL);
        s.seg.requireUsuarioORol(Collections.singletonList(OTRO), ROL);
        s.autenticar("jefe", "ADMIN");
        s.seg.requireUsuarioORol(Collections.singletonList(OTRO), ROL);
    }

    @Test
    void elAdminPeladoSinFilaPasa() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("ADMIN", null,
                        Collections.emptyList()));
        when(s.usuarioService.findByNickname("ADMIN")).thenReturn(Optional.empty());
        s.seg.requireUsuarioORol(Collections.singletonList(OTRO), ROL);
    }

    @Test
    void unaSesionSinUsuarioSeRechaza() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("fantasma", null,
                        Collections.emptyList()));
        when(s.usuarioService.findByNickname("fantasma")).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, () -> s.seg.requireUsuarioORol(Collections.singletonList(OTRO), ROL));
    }

    @Test
    void usuariosDeLaFacturaSonElEmisorYElDuenioDeSuCajaODeLaDeSuVenta() {
        FacturaLegal conCaja = new FacturaLegal();
        conCaja.setUsuario(usuario(3L));
        conCaja.setCaja(cajaDe(4L));
        assertEquals(Arrays.asList(3L, 4L), FacturaLegalGraphQL.usuariosDe(conCaja));

        FacturaLegal sinCaja = new FacturaLegal();
        sinCaja.setUsuario(usuario(3L));
        Venta venta = new Venta();
        venta.setCaja(cajaDe(6L));
        sinCaja.setVenta(venta);
        assertEquals(Arrays.asList(3L, 6L), FacturaLegalGraphQL.usuariosDe(sinCaja));

        assertEquals(Arrays.asList(null, null), FacturaLegalGraphQL.usuariosDe(new FacturaLegal()));
    }

    @Test
    void reimprimirLaFacturaDeOtroSinRolSeRechazaSinBuscarItems() {
        s.autenticar("cajera");
        FacturaLegalGraphQL graphQL = s.resolver(FacturaLegalGraphQL.class);
        FacturaLegalService service = mock(FacturaLegalService.class);
        FacturaLegalItemService items = mock(FacturaLegalItemService.class);
        ReflectionTestUtils.setField(graphQL, "service", service);
        ReflectionTestUtils.setField(graphQL, "facturaLegalItemService", items);
        FacturaLegal ajena = new FacturaLegal();
        ajena.setUsuario(usuario(OTRO));
        ajena.setCaja(cajaDe(OTRO));
        when(service.findByIdAndSucursalId(10L, 24L)).thenReturn(ajena);

        assertThrows(GraphQLException.class, () -> graphQL.reimprimirFacturaLegal(10L, 24L, "ticket"));
        verifyNoInteractions(items);
    }

    @Test
    void reimprimirLaFacturaPropiaPasaElControl() {
        s.autenticar("cajera");
        FacturaLegalGraphQL graphQL = s.resolver(FacturaLegalGraphQL.class);
        FacturaLegalService service = mock(FacturaLegalService.class);
        FacturaLegalItemService items = mock(FacturaLegalItemService.class);
        ReflectionTestUtils.setField(graphQL, "service", service);
        ReflectionTestUtils.setField(graphQL, "facturaLegalItemService", items);
        FacturaLegal propia = new FacturaLegal();
        propia.setUsuario(usuario(RolCajaSoporte.USUARIO_ID));
        when(service.findByIdAndSucursalId(11L, 24L)).thenReturn(propia);

        graphQL.reimprimirFacturaLegal(11L, 24L, "ticket");
        verify(items).findByFacturaLegalId(11L, 24L);
    }

    @Test
    void reimprimirUnaFacturaInexistenteDiceQueNoExiste() {
        s.autenticar("cajera");
        FacturaLegalGraphQL graphQL = s.resolver(FacturaLegalGraphQL.class);
        ReflectionTestUtils.setField(graphQL, "service", mock(FacturaLegalService.class));
        GraphQLException e = assertThrows(GraphQLException.class, () -> graphQL.reimprimirFacturaLegal(12L, 24L, "t"));
        assertEquals("La factura 12 no existe", e.getMessage());
    }

    @Test
    void imprimirElBalanceDeOtraCajaSinRolSeRechazaSinImprimir() {
        s.autenticar("cajera");
        PdvCajaGraphQL graphQL = s.resolver(PdvCajaGraphQL.class);
        PdvCajaService service = mock(PdvCajaService.class);
        PdvCajaRepository repo = mock(PdvCajaRepository.class);
        when(service.getRepository()).thenReturn(repo);
        ReflectionTestUtils.setField(graphQL, "service", service);
        when(repo.findByIdAndSucursalId(1185L, 24L)).thenReturn(cajaDe(OTRO));

        assertThrows(GraphQLException.class, () -> graphQL.imprimirBalance(1185L, "ticket", "CAJA 1", 24L));
        verify(service, never()).imprimirBalance(any(), any(), any());

        when(repo.findByIdAndSucursalId(1185L, 24L)).thenReturn(cajaDe(RolCajaSoporte.USUARIO_ID));
        graphQL.imprimirBalance(1185L, "ticket", "CAJA 1", 24L);
        verify(service).imprimirBalance(any(), eq("ticket"), eq("CAJA 1"));
    }

    @Test
    void descargarElPdfOElXmlDeUnaFacturaAjenaSinRolSeRechaza() {
        s.autenticar("cajera");
        FacturaLegalGraphQL graphQL = s.resolver(FacturaLegalGraphQL.class);
        FacturaLegalService service = mock(FacturaLegalService.class);
        ReflectionTestUtils.setField(graphQL, "service", service);
        FacturaLegal ajena = new FacturaLegal();
        ajena.setUsuario(usuario(OTRO));
        when(service.findByIdAndSucursalId(10L, 24L)).thenReturn(ajena);

        assertThrows(GraphQLException.class, () -> graphQL.descargarPdfFacturaElectronica(10L, 24L));
        assertThrows(GraphQLException.class, () -> graphQL.descargarXmlFacturaElectronica(10L, 24L));
        verify(service, times(2)).findByIdAndSucursalId(10L, 24L);
    }

    @Test
    void imprimirElCreditoSinAdminSeRechazaSinBuscarlo() {
        s.autenticar("analista", ROL);
        VentaCreditoGraphQL graphQL = s.resolver(VentaCreditoGraphQL.class);
        com.franco.dev.service.financiero.VentaCreditoService creditos =
                mock(com.franco.dev.service.financiero.VentaCreditoService.class);
        ReflectionTestUtils.setField(graphQL, "service", creditos);

        assertThrows(GraphQLException.class, () -> graphQL.imprimirVentaCredito(1L, 24L, "ticket"));
        verifyNoInteractions(creditos);
    }

    @Test
    void imprimirElCreditoConAdminPasaElControl() {
        s.autenticar("jefe", "ADMIN");
        VentaCreditoGraphQL graphQL = s.resolver(VentaCreditoGraphQL.class);
        com.franco.dev.service.financiero.VentaCreditoService creditos =
                mock(com.franco.dev.service.financiero.VentaCreditoService.class);
        ReflectionTestUtils.setField(graphQL, "service", creditos);

        GraphQLException e = assertThrows(GraphQLException.class, () -> graphQL.imprimirVentaCredito(1L, 24L, "t"));
        assertEquals("Venta credito no encontrada", e.getMessage());
    }
}
