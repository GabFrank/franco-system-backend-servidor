package com.franco.dev.service.financiero;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.ChequeRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Read-model de tesorería (F8): consolida efectivo + banco por moneda. */
class TesoreriaReporteServiceTest {

    @Test
    void consolida_efectivo_y_banco_por_moneda() {
        CajaVirtualSaldoRepository cajaRepo = mock(CajaVirtualSaldoRepository.class);
        CuentaBancariaRepository bancoRepo = mock(CuentaBancariaRepository.class);
        // Gs (id 10): efectivo 500000, banco 200000/reservado 50000
        when(cajaRepo.saldoConsolidadoPorMoneda()).thenReturn(Arrays.<Object[]>asList(
                new Object[]{10L, "GUARANIES", new BigDecimal("500000")}));
        when(bancoRepo.saldoBancarioPorMoneda()).thenReturn(Arrays.<Object[]>asList(
                new Object[]{10L, "GUARANIES", new BigDecimal("200000"), new BigDecimal("50000")}));

        TesoreriaReporteService service = new TesoreriaReporteService(cajaRepo, bancoRepo, mock(com.franco.dev.repository.operaciones.SolicitudPagoRepository.class), mock(com.franco.dev.repository.financiero.ChequeRepository.class));
        List<TesoreriaReporteService.SaldoPorMoneda> out = service.saldoConsolidado();

        assertEquals(1, out.size());
        TesoreriaReporteService.SaldoPorMoneda gs = out.get(0);
        assertEquals(0, gs.getEfectivo().compareTo(new BigDecimal("500000")));
        assertEquals(0, gs.getBanco().compareTo(new BigDecimal("200000")));
        assertEquals(0, gs.getBancoReservado().compareTo(new BigDecimal("50000")));
        assertEquals(0, gs.getTotal().compareTo(new BigDecimal("700000")));
    }

    @Test
    void moneda_solo_en_banco_aparece() {
        CajaVirtualSaldoRepository cajaRepo = mock(CajaVirtualSaldoRepository.class);
        CuentaBancariaRepository bancoRepo = mock(CuentaBancariaRepository.class);
        when(cajaRepo.saldoConsolidadoPorMoneda()).thenReturn(Collections.emptyList());
        when(bancoRepo.saldoBancarioPorMoneda()).thenReturn(Arrays.<Object[]>asList(
                new Object[]{20L, "DOLARES", new BigDecimal("1000"), new BigDecimal("0")}));

        TesoreriaReporteService service = new TesoreriaReporteService(cajaRepo, bancoRepo, mock(com.franco.dev.repository.operaciones.SolicitudPagoRepository.class), mock(com.franco.dev.repository.financiero.ChequeRepository.class));
        List<TesoreriaReporteService.SaldoPorMoneda> out = service.saldoConsolidado();
        assertEquals(1, out.size());
        assertEquals(0, out.get(0).getTotal().compareTo(new BigDecimal("1000")));
    }

    /**
     * FIX #2: el aging de CPP debe consultar la deuda abierta ({SOLICITADO, PARCIAL}),
     * NO la lista vieja {PENDIENTE, PARCIAL}. Una solicitud SOLICITADO es deuda real y
     * debe contar; PENDIENTE (borrador) no debe consultarse.
     */
    @Test
    void aging_consulta_deuda_abierta_y_cuenta_solicitado() {
        SolicitudPagoRepository solicitudRepo = mock(SolicitudPagoRepository.class);
        SolicitudPago solicitado = solicitud(1L, SolicitudPagoEstado.SOLICITADO,
                1_000_000.0, BigDecimal.ZERO, LocalDateTime.now().minusDays(2)); // vencido
        when(solicitudRepo.findByEstadoIn(anyList())).thenReturn(Collections.singletonList(solicitado));

        TesoreriaReporteService service = new TesoreriaReporteService(
                mock(CajaVirtualSaldoRepository.class), mock(CuentaBancariaRepository.class),
                solicitudRepo, mock(ChequeRepository.class));
        TesoreriaReporteService.Aging aging = service.agingCpp();

        // La solicitud SOLICITADO vencida entra en "vencido"
        assertEquals(0, aging.getVencido().compareTo(new BigDecimal("1000000")));
        assertEquals(0, aging.getPorVencer().compareTo(BigDecimal.ZERO));

        // Y se consultó exactamente la deuda abierta, sin PENDIENTE
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SolicitudPagoEstado>> captor = ArgumentCaptor.forClass(List.class);
        verify(solicitudRepo).findByEstadoIn(captor.capture());
        assertTrue(captor.getValue().contains(SolicitudPagoEstado.SOLICITADO));
        assertTrue(captor.getValue().contains(SolicitudPagoEstado.PARCIAL));
        assertFalse(captor.getValue().contains(SolicitudPagoEstado.PENDIENTE));
    }

    /**
     * FIX #2: próximos vencimientos debe incluir una solicitud SOLICITADO dentro de la ventana.
     */
    @Test
    void proximos_vencimientos_incluye_solicitado() {
        SolicitudPagoRepository solicitudRepo = mock(SolicitudPagoRepository.class);
        SolicitudPago solicitado = solicitud(7L, SolicitudPagoEstado.SOLICITADO,
                500_000.0, new BigDecimal("100000"), LocalDateTime.now().plusDays(3));
        when(solicitudRepo.findByEstadoIn(anyList())).thenReturn(Collections.singletonList(solicitado));
        ChequeRepository chequeRepo = mock(ChequeRepository.class);
        when(chequeRepo.findByEstado(any())).thenReturn(Collections.emptyList());

        TesoreriaReporteService service = new TesoreriaReporteService(
                mock(CajaVirtualSaldoRepository.class), mock(CuentaBancariaRepository.class),
                solicitudRepo, chequeRepo);
        List<TesoreriaReporteService.Vencimiento> out = service.proximosVencimientos(30);

        assertEquals(1, out.size());
        TesoreriaReporteService.Vencimiento v = out.get(0);
        assertEquals("CPP", v.getTipo());
        assertEquals(7L, v.getReferenciaId());
        // saldo = total 500000 - pagado 100000 = 400000
        assertEquals(0, v.getMonto().compareTo(new BigDecimal("400000")));
    }

    private static SolicitudPago solicitud(Long id, SolicitudPagoEstado estado, Double montoTotal,
                                           BigDecimal montoPagado, LocalDateTime fechaPagoPropuesta) {
        SolicitudPago sp = new SolicitudPago();
        sp.setId(id);
        sp.setEstado(estado);
        sp.setMontoTotal(montoTotal);
        sp.setMontoPagado(montoPagado);
        sp.setFechaPagoPropuesta(fechaPagoPropuesta);
        return sp;
    }
}
