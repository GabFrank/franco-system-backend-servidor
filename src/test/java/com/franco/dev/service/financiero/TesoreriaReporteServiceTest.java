package com.franco.dev.service.financiero;

import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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
}
