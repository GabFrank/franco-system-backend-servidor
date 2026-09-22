package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.rrhh.AguinaldoRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalRepository;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.rrhh.PrestamoCuotaDescuentoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pago de liquidaciones por el hub de tesoreria (issue #300): las cuotas se validan antes de crear la
 * obligacion y de que el motor postee, y la obligacion reusada lleva el monto actual del documento.
 */
class PagoRrhhTesoreriaServiceCuotaTest {

    private LiquidacionSueldoRepository liquidacionRepository;
    private SolicitudPagoService solicitudPagoService;
    private PagoProveedorService motor;
    private PrestamoCuotaDescuentoService descuento;
    private PagoRrhhTesoreriaService service;
    private Moneda gs;

    @BeforeEach
    void setUp() {
        liquidacionRepository = mock(LiquidacionSueldoRepository.class);
        solicitudPagoService = mock(SolicitudPagoService.class);
        motor = mock(PagoProveedorService.class);
        descuento = mock(PrestamoCuotaDescuentoService.class);
        service = new PagoRrhhTesoreriaService(liquidacionRepository, mock(LiquidacionFinalRepository.class),
                mock(AguinaldoRepository.class), solicitudPagoService, motor, mock(MonedaService.class), descuento);
        gs = new Moneda();
        gs.setId(1L);

        SolicitudPago nueva = new SolicitudPago();
        nueva.setId(50L);
        when(solicitudPagoService.crearSolicitudVale(any(), any(), any(), any())).thenReturn(nueva);
    }

    private LiquidacionSueldo liquidacion(long id, long neto, Long solicitudId) {
        LiquidacionSueldo l = new LiquidacionSueldo();
        l.setId(id);
        l.setEstado(LiquidacionSueldoEstado.APROBADA);
        l.setTotalNeto(BigDecimal.valueOf(neto));
        l.setMoneda(gs);
        l.setSolicitudPagoId(solicitudId);
        when(liquidacionRepository.findById(id)).thenReturn(Optional.of(l));
        when(liquidacionRepository.lockById(id)).thenReturn(Optional.of(l));
        return l;
    }

    private SolicitudPago solicitud(long id, double montoTotal, long montoPagado) {
        SolicitudPago sp = new SolicitudPago();
        sp.setId(id);
        sp.setEstado(montoPagado > 0 ? SolicitudPagoEstado.PARCIAL : SolicitudPagoEstado.SOLICITADO);
        sp.setMontoTotal(montoTotal);
        sp.setMontoPagado(BigDecimal.valueOf(montoPagado));
        when(solicitudPagoService.findById(id)).thenReturn(Optional.of(sp));
        return sp;
    }

    private PagoRrhhTesoreriaService.PagoRrhhConLineas pago(long documentoId, long monto) {
        PagoProveedorService.LineaPago linea = new PagoProveedorService.LineaPago();
        linea.setMonto(BigDecimal.valueOf(monto));
        linea.setMontoSolicitud(BigDecimal.valueOf(monto));
        PagoRrhhTesoreriaService.PagoRrhhConLineas p = new PagoRrhhTesoreriaService.PagoRrhhConLineas();
        p.setConcepto(PagoRrhhTesoreriaService.ConceptoRrhh.LIQUIDACION);
        p.setDocumentoId(documentoId);
        p.setLineas(List.of(linea));
        return p;
    }

    @Test
    void cuotaCambiadaRechazaAntesDeCrearLaObligacionYDePagar() {
        liquidacion(5L, 1_500_000, null);
        doThrow(new GraphQLException("Vuelva a borrador y regenere")).when(descuento).validarLiquidacion(5L);

        assertThrows(GraphQLException.class, () -> service.pagarRrhhMixto(List.of(pago(5L, 1_500_000)), null));

        verify(solicitudPagoService, never()).crearSolicitudVale(any(), any(), any(), any());
        verify(motor, never()).pagarLoteMixtoObligacionesRrhh(any(), any());
    }

    @Test
    void obligacionSinPagosConMontoViejoSeActualizaAlTotalActual() {
        // Se pago, se anulo el pago (la obligacion quedo con 1.000.000) y se regenero a 900.000.
        liquidacion(5L, 900_000, 40L);
        SolicitudPago sp = solicitud(40L, 1_000_000.0, 0);

        service.pagarRrhhMixto(List.of(pago(5L, 900_000)), null);

        assertEquals(900_000.0, sp.getMontoTotal());
        assertTrue(sp.getObservaciones().contains("MONTO AJUSTADO") && sp.getObservaciones().contains("900000"),
                "el ajuste tiene que quedar asentado, observaciones: " + sp.getObservaciones());
        verify(solicitudPagoService).save(sp);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PagoProveedorService.SolicitudConLineas>> lote = ArgumentCaptor.forClass(List.class);
        verify(motor).pagarLoteMixtoObligacionesRrhh(lote.capture(), any());
        assertEquals(40L, lote.getValue().get(0).getSolicitudId());
    }

    @Test
    void obligacionConPagosAplicadosYMontoDistintoSeRechaza() {
        liquidacion(5L, 900_000, 40L);
        solicitud(40L, 1_000_000.0, 100_000);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.pagarRrhhMixto(List.of(pago(5L, 800_000)), null));
        assertTrue(e.getMessage().contains("pagos aplicados"), e.getMessage());
        verify(motor, never()).pagarLoteMixtoObligacionesRrhh(any(), any());
    }

    @Test
    void unLoteDesordenadoSeValidaEnOrdenDeId() {
        liquidacion(9L, 100, null);
        liquidacion(5L, 100, null);

        service.pagarRrhhMixto(List.of(pago(9L, 100), pago(5L, 100)), null);

        InOrder orden = inOrder(descuento);
        orden.verify(descuento).validarLiquidacion(5L);
        orden.verify(descuento).validarLiquidacion(9L);
    }

    @Test
    void obligacionParcialHeredadaSeTerminaDePagarPorElSaldoRestante() {
        // Issue #302: una obligacion que alguien pago en parte por el camino generico deja de verse en compras;
        // el hub la termina de pagar por lo que falta, no por el total.
        liquidacion(5L, 1_000_000, 40L);
        solicitud(40L, 1_000_000.0, 400_000);

        service.pagarRrhhMixto(List.of(pago(5L, 600_000)), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PagoProveedorService.SolicitudConLineas>> lote = ArgumentCaptor.forClass(List.class);
        verify(motor).pagarLoteMixtoObligacionesRrhh(lote.capture(), any());
        assertEquals(40L, lote.getValue().get(0).getSolicitudId());
    }
}
