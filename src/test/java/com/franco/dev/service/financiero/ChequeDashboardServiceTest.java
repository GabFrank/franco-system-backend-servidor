package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.repository.financiero.ChequeRepository;
import com.franco.dev.repository.financiero.ChequeraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Dashboard de cheques: agrupación por día de pago. */
class ChequeDashboardServiceTest {

    private ChequeRepository chequeRepository;
    private ChequeraRepository chequeraRepository;
    private ChequeDashboardService service;

    @BeforeEach
    void setUp() {
        chequeRepository = mock(ChequeRepository.class);
        chequeraRepository = mock(ChequeraRepository.class);
        service = new ChequeDashboardService(chequeRepository, chequeraRepository);
    }

    private Cheque cheque(String fechaPago, double total) {
        Cheque c = new Cheque();
        c.setFechaPago(LocalDateTime.parse(fechaPago + "T00:00:00"));
        c.setTotal(total);
        return c;
    }

    @Test
    void resumen_por_dia_agrupa_y_suma_por_fecha_de_pago() {
        when(chequeRepository.filtrarPorFechaPago(any(), any(), any(), any(), any())).thenReturn(Arrays.asList(
                cheque("2026-08-12", 100000),
                cheque("2026-08-12", 50000),   // mismo día → se suma
                cheque("2026-08-20", 30000)
        ));

        List<ChequeDashboardService.ResumenDia> r = service.resumenPorDia(
                LocalDateTime.now(), LocalDateTime.now(), null, null, null);

        assertEquals(2, r.size());
        assertEquals("2026-08-12", r.get(0).getFecha());   // ordenado por fecha (TreeMap)
        assertEquals(150000.0, r.get(0).getTotal());
        assertEquals(2, r.get(0).getCantidad());
        assertEquals("2026-08-20", r.get(1).getFecha());
        assertEquals(30000.0, r.get(1).getTotal());
        assertEquals(1, r.get(1).getCantidad());
    }
}
