package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.LucroPorFuncionarioDto;
import com.franco.dev.repository.operaciones.VentaItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** #615: «Todas» llega del desktop como [null] y el reporte salía vacío por un IN (NULL). */
class LucroPorFuncionarioTodasTest {

    private static final String INICIO = "2026-08-01T07:00:00";
    private static final String FIN = "2026-09-01T06:59:00";
    private static final List<Long> RELLENO = Collections.singletonList(-1L);

    private VentaItemRepository repository;
    private VentaService service;

    @BeforeEach
    void setUp() {
        repository = mock(VentaItemRepository.class);
        // VentaService tiene ~20 dependencias por constructor; para este método solo hace falta el repo.
        service = mock(VentaService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "ventaItemRepository", repository);
        when(repository.findLucroPorFuncionarioNative(anyList(), any(), any(), anyList(), anyList(), any(), any(),
                anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Arrays.asList(
                        new Object[]{7L, "ANA", 600.0, 10.0, 1000.0, 50.0, 0.0},
                        new Object[]{9L, "LUIS", 100.0, 4.0, 3000.0, 0.0, 20.0}));
    }

    @Test
    void todasNoFiltraPorSucursal() {
        List<LucroPorFuncionarioDto> r = service.findLucroPorFuncionarios(INICIO, FIN,
                Collections.singletonList(null), null, null, null, null);

        verify(repository).findLucroPorFuncionarioNative(eq(RELLENO), any(), any(), eq(RELLENO), eq(RELLENO),
                isNull(), isNull(), eq(false), eq(false), eq(false));
        assertEquals(2, r.size(), "antes devolvía vacío");
    }

    @Test
    void listaNulaOVaciaTambienEsTodas() {
        service.findLucroPorFuncionarios(INICIO, FIN, null, null, null, null, null);
        service.findLucroPorFuncionarios(INICIO, FIN, Collections.emptyList(), null, null, null, null);

        verify(repository, times(2)).findLucroPorFuncionarioNative(eq(RELLENO), any(), any(), anyList(),
                anyList(), any(), any(), eq(false), anyBoolean(), anyBoolean());
    }

    @Test
    void sucursalesElegidasFiltranSinLosNull() {
        service.findLucroPorFuncionarios(INICIO, FIN, Arrays.asList(3L, null, 5L, 3L),
                Arrays.asList(null, 7L), Collections.singletonList(11L), 2L, null);

        verify(repository).findLucroPorFuncionarioNative(eq(Arrays.asList(3L, 5L)), any(), any(),
                eq(Collections.singletonList(7L)), eq(Collections.singletonList(11L)), eq(2L), isNull(),
                eq(true), eq(true), eq(true));
    }

    @Test
    void calculaYOrdenaComoAntes() {
        List<LucroPorFuncionarioDto> r = service.findLucroPorFuncionarios(INICIO, FIN, null, null, null, null,
                null);

        assertEquals(9L, r.get(0).getUsuarioId(), "mayor total de venta primero");
        LucroPorFuncionarioDto ana = r.get(1);
        assertEquals(350.0, ana.getLucro(), 1e-9, "1000 - 600 - 50 de descuento");
        assertEquals(100.0, ana.getVentaMedia(), 1e-9);
        assertEquals(2920.0, r.get(0).getLucro(), 1e-9, "3000 - 100 + 20 de aumento");
    }
}
