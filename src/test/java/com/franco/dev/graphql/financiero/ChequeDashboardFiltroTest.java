package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.enums.EstadoCheque;
import com.franco.dev.service.financiero.ChequeDashboardService;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/** El filtro del tablero de cheques (issue #601) y su compatibilidad con un desktop anterior. */
class ChequeDashboardFiltroTest {

    @Test
    void un_desktop_anterior_sigue_filtrando_por_fecha_de_pago_y_su_estado() {
        ChequeDashboardService.Filtro f = ChequeDashboardGraphQL.filtro("2026-11-01 00:00:00", "2026-11-30 23:59:59",
                null, null, EstadoCheque.DIFERIDO, null, null, null, null);
        assertEquals(ChequeDashboardService.TipoFecha.PAGO, f.getTipoFecha());
        assertEquals(Collections.singletonList(EstadoCheque.DIFERIDO), f.getEstados());
    }

    @Test
    void emitidos_hoy_por_fecha_de_emision_y_todos_los_estados() {
        ChequeDashboardService.Filtro f = ChequeDashboardGraphQL.filtro("2026-11-12 00:00:00", "2026-11-12 23:59:59",
                null, null, null, "EMISION", null, "agua", 5L);
        assertEquals(ChequeDashboardService.TipoFecha.EMISION, f.getTipoFecha());
        assertTrue(f.getEstados().isEmpty(), "sin estados = todos");
        assertEquals(5L, f.getProveedorId());
    }

    @Test
    void la_lista_de_estados_gana_sobre_el_estado_suelto() {
        ChequeDashboardService.Filtro f = ChequeDashboardGraphQL.filtro("2026-11-01 00:00:00", "2026-11-30 23:59:59",
                null, null, EstadoCheque.COBRADO, "pago", Arrays.asList(EstadoCheque.DIFERIDO, EstadoCheque.EMITIDO), null, null);
        assertEquals(Arrays.asList(EstadoCheque.DIFERIDO, EstadoCheque.EMITIDO), f.getEstados());
    }

    @Test
    void un_tipo_de_fecha_desconocido_se_rechaza() {
        assertThrows(GraphQLException.class, () -> ChequeDashboardGraphQL.filtro("2026-11-01 00:00:00",
                "2026-11-30 23:59:59", null, null, null, "VENCIMIENTO", null, null, null));
    }
}
