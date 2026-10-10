package com.franco.dev.graphql.operaciones;

import com.franco.dev.service.operaciones.ControlStockNegativoService;
import com.franco.dev.service.operaciones.InventarioSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** El fin del rango incluye el ultimo minuto completo; el inicio no se toca. */
class ControlStockNegativoGraphQLRangoTest {

    private ControlStockNegativoService service;
    private ControlStockNegativoGraphQL resolver;

    @BeforeEach
    void preparar() {
        service = mock(ControlStockNegativoService.class);
        InventarioSecurityService seg = mock(InventarioSecurityService.class);
        resolver = new ControlStockNegativoGraphQL(service, seg);
    }

    private LocalDateTime[] consultar(String inicio, String fin) {
        resolver.controlStockNegativo(inicio, fin, null, null, null, null, 0, 15);
        ArgumentCaptor<LocalDateTime> ini = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fn = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(service).buscar(ini.capture(), fn.capture(), any(), any(), any(), any(), anyInt(), anyInt());
        return new LocalDateTime[]{ini.getValue(), fn.getValue()};
    }

    @Test
    void finConPrecisionDeMinutoLlegaHastaElUltimoNanosegundo() {
        LocalDateTime[] r = consultar("2026-10-01 00:00", "2026-10-10 23:59");
        assertEquals(LocalDateTime.of(2026, 10, 10, 23, 59, 59, 999_999_999), r[1]);
    }

    @Test
    void finQueYaTraeSegundosNoSeModifica() {
        LocalDateTime[] r = consultar("2026-10-01 00:00", "2026-10-10 23:59:30");
        assertEquals(LocalDateTime.of(2026, 10, 10, 23, 59, 30), r[1]);
    }

    @Test
    void elInicioNuncaSeModifica() {
        LocalDateTime[] r = consultar("2026-10-01 00:00", "2026-10-10 23:59");
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0, 0), r[0]);
    }
}
