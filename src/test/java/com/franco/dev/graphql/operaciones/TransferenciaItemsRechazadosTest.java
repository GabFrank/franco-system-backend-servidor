package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.enums.TransferenciaItemMotivoRechazo;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Defaults y topes de transferenciaItemsRechazados: la consulta recorre transferencia_item entera. */
class TransferenciaItemsRechazadosTest {

    private TransferenciaItemService service;
    private TransferenciaItemGraphQL resolver;

    private final ArgumentCaptor<TransferenciaItemMotivoRechazo> motivo = ArgumentCaptor.forClass(TransferenciaItemMotivoRechazo.class);
    private final ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
    private final ArgumentCaptor<LocalDateTime> hasta = ArgumentCaptor.forClass(LocalDateTime.class);
    private final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

    @BeforeEach
    void preparar() {
        service = mock(TransferenciaItemService.class);
        resolver = new TransferenciaItemGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "seg", mock(TesoreriaSecurityService.class));
    }

    private void consultar(TransferenciaItemMotivoRechazo m, String d, String h, Integer page, Integer size) {
        resolver.transferenciaItemsRechazados(m, 1L, 2L, 3L, d, h, page, size);
        verify(service).findRechazados(motivo.capture(), eq(1L), eq(2L), eq(3L), desde.capture(), hasta.capture(), pageable.capture());
    }

    @Test
    void sinMotivoBuscaFaltantes() {
        consultar(null, "2026-10-01", "2026-10-31", 0, 20);
        assertEquals(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO, motivo.getValue());
    }

    @Test
    void respetaElMotivoPedido() {
        consultar(TransferenciaItemMotivoRechazo.PRODUCTO_AVERIADO, "2026-10-01", "2026-10-31", 0, 20);
        assertEquals(TransferenciaItemMotivoRechazo.PRODUCTO_AVERIADO, motivo.getValue());
    }

    @Test
    void hastaSinHoraIncluyeElDiaEntero() {
        consultar(null, "2026-10-01", "2026-10-31", 0, 20);
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), desde.getValue());
        assertEquals(LocalDate.of(2026, 10, 31).atTime(LocalTime.of(23, 59, 59, 999_999_000)), hasta.getValue());
    }

    @Test
    void hastaConHoraNoSeModifica() {
        consultar(null, "2026-10-01", "2026-10-31 15:30", 0, 20);
        assertEquals(LocalDateTime.of(2026, 10, 31, 15, 30), hasta.getValue());
    }

    @Test
    void hastaConMedianocheExplicitaNoSeModifica() {
        consultar(null, "2026-10-01", "2026-10-31 00:00", 0, 20);
        assertEquals(LocalDateTime.of(2026, 10, 31, 0, 0), hasta.getValue());
    }

    @Test
    void sinFechasSonLosUltimos30Dias() {
        consultar(null, null, null, 0, 20);
        assertEquals(LocalDate.now(), hasta.getValue().toLocalDate());
        assertEquals(LocalDate.now().minusDays(30).atStartOfDay(), desde.getValue());
    }

    @Test
    void rangoDe366DiasPasaYDe367SeRechaza() {
        consultar(null, "2025-01-01", "2026-01-02", 0, 20);
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.transferenciaItemsRechazados(null, null, null, null, "2025-01-01", "2026-01-03", 0, 20));
        assertTrue(e.getMessage().contains("366"));
    }

    @Test
    void desdePosteriorAHastaSeRechaza() {
        assertThrows(GraphQLException.class,
                () -> resolver.transferenciaItemsRechazados(null, null, null, null, "2026-11-02", "2026-11-01", 0, 20));
        verify(service, never()).findRechazados(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sizeSeAcotaA100YPageNegativaVaALaPrimera() {
        consultar(null, "2026-10-01", "2026-10-31", -3, 5000);
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
    }

    @Test
    void sizeNuloOCeroUsa20() {
        consultar(null, "2026-10-01", "2026-10-31", null, 0);
        assertEquals(20, pageable.getValue().getPageSize());
    }
}
