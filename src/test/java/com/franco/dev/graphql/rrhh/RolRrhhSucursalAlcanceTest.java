package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.FuentePago;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.graphql.financiero.RolCajaSoporte;
import com.franco.dev.service.rrhh.dto.PagoRrhhPendienteDto;
import com.franco.dev.service.financiero.PagoRrhhTesoreriaService;
import com.franco.dev.service.rrhh.LiquidacionSueldoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * El rol acotado RRHH SUCURSAL trabaja solo con funcionarios de las sucursales que cubren las cajas que
 * puede mover: liquida a esos, ve lo de esos y paga, en efectivo y desde su caja, lo ya aprobado de esos.
 */
class RolRrhhSucursalAlcanceTest {

    private static final Long CAJA = 19L;
    private static final Long FUNCIONARIO_MIO = 100L;
    private static final Long FUNCIONARIO_AJENO = 200L;
    private static final Long LIQUIDACION_MIA = 10L;
    private static final Long LIQUIDACION_AJENA = 20L;

    private final RolRrhhSucursalSoporte s = new RolRrhhSucursalSoporte();
    private final LiquidacionSueldoService liquidaciones = mock(LiquidacionSueldoService.class);
    private final PagoRrhhTesoreriaService pagos = mock(PagoRrhhTesoreriaService.class);
    private LiquidacionSueldoGraphQL resolver;
    private PagoRrhhTesoreriaGraphQL pagoResolver;

    @BeforeEach
    void setUp() {
        resolver = s.resolver(LiquidacionSueldoGraphQL.class);
        ReflectionTestUtils.setField(resolver, "service", liquidaciones);
        pagoResolver = new PagoRrhhTesoreriaGraphQL(pagos, s.seg, s.alcance);

        when(s.alcanceRepository.sucursalDeFuncionario(FUNCIONARIO_MIO)).thenReturn(Collections.singletonList(12L));
        when(s.alcanceRepository.sucursalDeFuncionario(FUNCIONARIO_AJENO)).thenReturn(Collections.singletonList(1L));
        when(s.alcanceRepository.sucursalDeLiquidacion(LIQUIDACION_MIA)).thenReturn(Collections.singletonList(23L));
        when(s.alcanceRepository.sucursalDeLiquidacion(LIQUIDACION_AJENA)).thenReturn(Collections.singletonList(1L));
        com.franco.dev.domain.financiero.CajaVirtualAcceso acceso = new com.franco.dev.domain.financiero.CajaVirtualAcceso();
        acceso.setPuedeLeer(true);
        acceso.setPuedeEscribir(true);
        when(s.caja.accesoRepository.findByCajaVirtualIdAndUsuarioId(CAJA, RolCajaSoporte.USUARIO_ID))
                .thenReturn(java.util.Optional.of(acceso));
    }

    @AfterEach
    void tearDown() {
        RolCajaSoporte.limpiar();
    }

    private void gerente() {
        s.autenticar("gerente", "RRHH SUCURSAL", "TESORERIA CAJA");
        s.aCargoDe(9L, 12L, 23L);
    }

    private static void noAutorizado(org.junit.jupiter.api.function.Executable e) {
        GraphQLException ex = assertThrows(GraphQLException.class, e);
        assertTrue(ex.getMessage().startsWith("No autorizado"), ex.getMessage());
    }

    // ── Por acción ──

    @Test
    void conSoloElRolAcotado_todoEsAcotado() {
        gerente();
        assertTrue(s.alcance.veAcotado());
        assertTrue(s.alcance.liquidaAcotado());
        assertTrue(s.alcance.pagaAcotado());
    }

    @Test
    void sumarUnRolDeLectura_noHabilitaALiquidarNiAPagarATodaLaEmpresa() {
        s.autenticar("gerente", "RRHH SUCURSAL", "RRHH VER");
        assertFalse(s.alcance.veAcotado());
        assertTrue(s.alcance.liquidaAcotado());
        assertTrue(s.alcance.pagaAcotado());
    }

    @Test
    void quienYaLiquidaTodaLaEmpresa_noQuedaAcotadoPorSumarElRol() {
        s.autenticar("rrhh", "RRHH LIQUIDAR", "RRHH SUCURSAL");
        assertFalse(s.alcance.liquidaAcotado());
        assertTrue(s.alcance.pagaAcotado());
    }

    @Test
    void unSuperusuario_nuncaEstaAcotado() {
        s.autenticar("ADMIN");
        assertFalse(s.alcance.veAcotado() || s.alcance.liquidaAcotado() || s.alcance.pagaAcotado());
    }

    // ── Alcance ──

    @Test
    void unFuncionarioSinSucursal_oQueNoExiste_quedaFuera() {
        gerente();
        when(s.alcanceRepository.sucursalDeFuncionario(300L)).thenReturn(Collections.singletonList(null));
        when(s.alcanceRepository.sucursalDeFuncionario(400L)).thenReturn(Collections.emptyList());

        noAutorizado(() -> s.alcance.exigirFuncionario(300L));
        noAutorizado(() -> s.alcance.exigirFuncionario(400L));
        noAutorizado(() -> s.alcance.exigirFuncionario(null));
        assertDoesNotThrow(() -> s.alcance.exigirFuncionario(FUNCIONARIO_MIO));
    }

    @Test
    void sinCajas_noTieneNingunFuncionarioNiConsulta() {
        s.autenticar("gerente", "RRHH SUCURSAL");
        s.aCargoDe();

        assertTrue(s.alcance.funcionariosActivosIds().isEmpty());
        assertTrue(s.alcance.buscarFuncionarios("a", true).isEmpty());
        noAutorizado(() -> s.alcance.exigirFuncionario(FUNCIONARIO_MIO));
        verify(s.alcanceRepository, never()).funcionariosActivosIds(anyList());
    }

    // ── Liquidaciones ──

    @Test
    void generaElBorradorDeUnFuncionarioSuyo_yNoElDeUnoAjeno() {
        gerente();

        resolver.generarLiquidacionBorrador(FUNCIONARIO_MIO, "2026-10", null);
        noAutorizado(() -> resolver.generarLiquidacionBorrador(FUNCIONARIO_AJENO, "2026-10", null));

        verify(liquidaciones).generarBorrador(FUNCIONARIO_MIO, "2026-10", null);
        verify(liquidaciones, never()).generarBorrador(eq(FUNCIONARIO_AJENO), any(), any());
    }

    @Test
    void elLoteDeTodos_sonLosActivosDeSusSucursales() {
        gerente();
        when(s.alcanceRepository.funcionariosActivosIds(Arrays.asList(9L, 12L, 23L))).thenReturn(Arrays.asList(100L, 101L));

        resolver.generarLiquidacionesLote(null, "2026-10", null);

        verify(liquidaciones).generarLote(Arrays.asList(100L, 101L), "2026-10", null);
    }

    @Test
    void elLoteDeTodosSinFuncionarios_noLlamaAlServicio() {
        // Para el servicio una lista vacía es toda la empresa.
        gerente();
        when(s.alcanceRepository.funcionariosActivosIds(anyList())).thenReturn(Collections.emptyList());

        assertEquals(0, resolver.generarLiquidacionesLote(Collections.emptyList(), "2026-10", null));

        verifyNoInteractions(liquidaciones);
    }

    @Test
    void elLoteConUnFuncionarioAjeno_seRechazaEntero() {
        gerente();

        noAutorizado(() -> resolver.generarLiquidacionesLote(Arrays.asList(100, 200), "2026-10", null));

        verifyNoInteractions(liquidaciones);
    }

    @Test
    void quienLiquidaTodaLaEmpresa_sigueGenerandoElLoteDeSiempre() {
        s.autenticar("rrhh", "RRHH LIQUIDAR");

        resolver.generarLiquidacionesLote(null, "2026-10", null);

        verify(liquidaciones).generarLote(null, "2026-10", null);
    }

    @Test
    void elListado_esSoloDeSusSucursales() {
        gerente();

        resolver.liquidacionesPage(0, 20, null, null, "2026-10", LiquidacionSueldoEstado.BORRADOR);

        verify(liquidaciones).findPageEnSucursales(Arrays.asList(9L, 12L, 23L), null, null, "2026-10",
                LiquidacionSueldoEstado.BORRADOR, PageRequest.of(0, 20));
        verify(liquidaciones, never()).findPage(any(), any(), any(), any(), any());
    }

    @Test
    void elListadoSinSucursales_estaVacioYNoConsulta() {
        s.autenticar("gerente", "RRHH SUCURSAL");
        s.aCargoDe();

        assertEquals(0, resolver.liquidacionesPage(0, 20, null, null, null, null).getTotalElements());

        verifyNoInteractions(liquidaciones);
    }

    @Test
    void veYEditaUnaLiquidacionSuya_yNoUnaAjena() {
        gerente();
        when(s.alcanceRepository.sucursalDeItemDeLiquidacion(55L)).thenReturn(Collections.singletonList(1L));

        resolver.liquidacionSueldo(LIQUIDACION_MIA);
        resolver.agregarItemLiquidacion(LIQUIDACION_MIA, "BONO", BigDecimal.TEN, null, null);
        noAutorizado(() -> resolver.liquidacionSueldo(LIQUIDACION_AJENA));
        noAutorizado(() -> resolver.liquidacionItems(LIQUIDACION_AJENA));
        noAutorizado(() -> resolver.agregarItemLiquidacion(LIQUIDACION_AJENA, "BONO", BigDecimal.TEN, null, null));
        noAutorizado(() -> resolver.editarItemLiquidacion(55L, "X", BigDecimal.ONE, null, null));
        noAutorizado(() -> resolver.eliminarItemLiquidacion(55L));

        verify(liquidaciones, never()).editarItem(any(), any(), any(), any(), any());
        verify(liquidaciones, never()).eliminarItem(any());
    }

    // ── Pago desde la caja ──

    private PagoRrhhTesoreriaGraphQL.PagoRrhhConLineasWrapper pago(PagoRrhhTesoreriaService.ConceptoRrhh concepto,
                                                                 Long documentoId, FuentePago fuente, Long cajaId) {
        PagoProveedorGraphQLLinea l = new PagoProveedorGraphQLLinea(fuente, cajaId);
        PagoRrhhTesoreriaGraphQL.PagoRrhhConLineasWrapper w = new PagoRrhhTesoreriaGraphQL.PagoRrhhConLineasWrapper();
        w.setConcepto(concepto);
        w.setDocumentoId(documentoId);
        w.setLineas(Collections.singletonList(l.wrapper()));
        return w;
    }

    /** Arma la línea por el wrapper real del resolver, para pasar por el mismo mapeo que un pedido. */
    private static class PagoProveedorGraphQLLinea {
        private final FuentePago fuente;
        private final Long cajaId;

        PagoProveedorGraphQLLinea(FuentePago fuente, Long cajaId) {
            this.fuente = fuente;
            this.cajaId = cajaId;
        }

        com.franco.dev.graphql.financiero.PagoProveedorGraphQL.LineaPagoInputWrapper wrapper() {
            com.franco.dev.graphql.financiero.PagoProveedorGraphQL.LineaPagoInputWrapper w =
                    new com.franco.dev.graphql.financiero.PagoProveedorGraphQL.LineaPagoInputWrapper();
            w.setFuente(fuente);
            w.setCajaVirtualId(cajaId);
            w.setMonedaId(1L);
            w.setMonto(1000000d);
            return w;
        }
    }

    @Test
    void pagaEnEfectivoDesdeSuCajaUnaLiquidacionDeSusSucursales() {
        gerente();

        pagoResolver.pagarRrhhMixto(Collections.singletonList(
                pago(PagoRrhhTesoreriaService.ConceptoRrhh.LIQUIDACION, LIQUIDACION_MIA, FuentePago.CAJA_MAYOR, CAJA)));

        verify(pagos).exigirEfectivoEnLaMonedaDelDocumento(anyList());
        verify(pagos).pagarRrhhMixto(anyList(), any());
    }

    @Test
    void noPagaUnaLiquidacionAjena_niUnAguinaldo_niPorBanco_niDesdeUnaCajaQueNoMueve() {
        gerente();

        noAutorizado(() -> pagoResolver.pagarRrhhMixto(Collections.singletonList(
                pago(PagoRrhhTesoreriaService.ConceptoRrhh.LIQUIDACION, LIQUIDACION_AJENA, FuentePago.CAJA_MAYOR, CAJA))));
        noAutorizado(() -> pagoResolver.pagarRrhhMixto(Collections.singletonList(
                pago(PagoRrhhTesoreriaService.ConceptoRrhh.AGUINALDO, 5L, FuentePago.CAJA_MAYOR, CAJA))));
        noAutorizado(() -> pagoResolver.pagarRrhhMixto(Collections.singletonList(
                pago(PagoRrhhTesoreriaService.ConceptoRrhh.LIQUIDACION, LIQUIDACION_MIA, FuentePago.CUENTA_BANCARIA, null))));
        assertThrows(GraphQLException.class, () -> pagoResolver.pagarRrhhMixto(Collections.singletonList(
                pago(PagoRrhhTesoreriaService.ConceptoRrhh.LIQUIDACION, LIQUIDACION_MIA, FuentePago.CAJA_MAYOR, 18L))));

        verify(pagos, never()).pagarRrhhMixto(anyList(), any());
    }

    @Test
    void sinRolDeCaja_elRolAcotadoDeRrhhNoPaga() {
        s.autenticar("gerente", "RRHH SUCURSAL");
        s.aCargoDe(9L, 12L, 23L);

        noAutorizado(() -> pagoResolver.pagarRrhhMixto(Collections.singletonList(
                pago(PagoRrhhTesoreriaService.ConceptoRrhh.LIQUIDACION, LIQUIDACION_MIA, FuentePago.CAJA_MAYOR, CAJA))));

        verify(pagos, never()).pagarRrhhMixto(anyList(), any());
    }

    @Test
    void quienPagaTodaLaEmpresa_noPasaPorElControlAcotado() {
        s.autenticar("rrhh", "RRHH PAGAR");

        pagoResolver.pagarRrhhMixto(Collections.singletonList(
                pago(PagoRrhhTesoreriaService.ConceptoRrhh.AGUINALDO, 5L, FuentePago.CUENTA_BANCARIA, null)));

        verify(pagos, never()).exigirEfectivoEnLaMonedaDelDocumento(anyList());
        verify(pagos).pagarRrhhMixto(anyList(), any());
    }

    @Test
    void losPendientesDePago_sonSoloLosDeSusSucursales() {
        gerente();
        when(pagos.listarLiquidacionesPendientes()).thenReturn(Arrays.asList(pendiente(12L), pendiente(1L), pendiente(null)));

        List<PagoRrhhPendienteDto> vistos = pagoResolver.liquidacionesPendientesPago();

        assertEquals(1, vistos.size());
        assertEquals(12L, vistos.get(0).getFuncionario().getSucursal().getId());
    }

    private static PagoRrhhPendienteDto pendiente(Long sucursalId) {
        Funcionario f = new Funcionario();
        if (sucursalId != null) {
            Sucursal suc = new Sucursal();
            suc.setId(sucursalId);
            f.setSucursal(suc);
        }
        PagoRrhhPendienteDto d = new PagoRrhhPendienteDto();
        d.setFuncionario(f);
        return d;
    }
}
