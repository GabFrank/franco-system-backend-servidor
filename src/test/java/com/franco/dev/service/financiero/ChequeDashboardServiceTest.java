package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.domain.financiero.enums.EstadoCheque;
import com.franco.dev.domain.financiero.enums.EstadoChequera;
import com.franco.dev.repository.financiero.ChequeRepository;
import com.franco.dev.repository.financiero.ChequeraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Dashboard de cheques: agrupación por día de pago. */
class ChequeDashboardServiceTest {

    private ChequeRepository chequeRepository;
    private ChequeraRepository chequeraRepository;
    private ChequeDashboardService service;
    private ChequeGestionService chequeGestionService;

    @BeforeEach
    void setUp() {
        chequeRepository = mock(ChequeRepository.class);
        chequeraRepository = mock(ChequeraRepository.class);
        chequeGestionService = mock(ChequeGestionService.class);
        service = new ChequeDashboardService(chequeRepository, chequeraRepository, chequeGestionService);
    }

    private Cheque cheque(String fechaPago, double total) {
        Cheque c = new Cheque();
        c.setFechaPago(LocalDateTime.parse(fechaPago + "T00:00:00"));
        c.setTotal(total);
        return c;
    }

    @Test
    void resumen_por_dia_agrupa_y_suma_por_fecha_de_pago() {
        // El resumen pasa por el filtro del tablero (issue #601); sin tipo de fecha, por fecha de pago.
        when(chequeRepository.filtrarTablero(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any())).thenReturn(Arrays.asList(
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

    private static Chequera chequera(long id, EstadoChequera estado) {
        Chequera ch = new Chequera();
        ch.setId(id);
        ch.setEstado(estado);
        return ch;
    }

    @Test
    void la_chequera_agotada_con_cheques_pendientes_sigue_en_los_saldos() {
        // Antes solo entraban las ACTIVA: al agotarse la chequera su diferido desaparecía de lo pendiente.
        Chequera activa = chequera(1L, EstadoChequera.ACTIVA);
        Chequera agotada = chequera(2L, EstadoChequera.AGOTADA);
        Chequera agotadaSinPendientes = chequera(3L, EstadoChequera.AGOTADA);
        Cheque diferido = cheque("2026-12-20", 200000);
        diferido.setChequera(agotada);
        Cheque emitido = cheque("2026-11-12", 300000);
        emitido.setChequera(agotada);
        when(chequeraRepository.findByEstadoOrderByIdDesc(EstadoChequera.ACTIVA)).thenReturn(Collections.singletonList(activa));
        when(chequeRepository.filtrarPorFechaPago(any(), any(), isNull(), isNull(), eq(EstadoCheque.DIFERIDO)))
                .thenReturn(Collections.singletonList(diferido));
        when(chequeRepository.filtrarPorFechaPago(any(), any(), isNull(), isNull(), eq(EstadoCheque.EMITIDO)))
                .thenReturn(Collections.singletonList(emitido));
        // Un diferido con pago después de «hasta»: no suma a lo pendiente, pero su chequera igual entra.
        Chequera agotadaFutura = chequera(4L, EstadoChequera.AGOTADA);
        Cheque futuro = cheque("2027-03-01", 700000);
        futuro.setChequera(agotadaFutura);
        LocalDateTime hasta = LocalDateTime.parse("2026-12-31T23:59:00");
        when(chequeRepository.filtrarPorFechaPago(any(), argThat(h -> h != null && h.getYear() == 9999), isNull(), isNull(),
                eq(EstadoCheque.DIFERIDO))).thenReturn(Arrays.asList(diferido, futuro));

        List<ChequeDashboardService.SaldoChequera> r = service.saldosPorChequera(hasta, null);

        assertEquals(3, r.size());
        assertEquals(1L, r.get(0).getChequera().getId());
        assertEquals(0.0, r.get(0).getPendienteHastaFecha());
        assertEquals(2L, r.get(2).getChequera().getId());
        assertEquals(500000.0, r.get(2).getPendienteHastaFecha());
        assertEquals(4L, r.get(1).getChequera().getId());
        assertEquals(0.0, r.get(1).getPendienteHastaFecha());
        assertTrue(r.stream().noneMatch(x -> x.getChequera() == agotadaSinPendientes));
    }
}
