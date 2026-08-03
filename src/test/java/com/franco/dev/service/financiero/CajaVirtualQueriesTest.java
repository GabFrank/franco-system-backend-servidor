package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cubre el massaging de parámetros de los filtros nuevos usados por la lista de cajas
 * y el dashboard: normalización de nombre vacío a null y conversión de fechas string a
 * LocalDateTime (vacío -> null), delegando en el repositorio con los valores correctos.
 */
class CajaVirtualQueriesTest {

    private CajaVirtualRepository cajaRepo;
    private CajaVirtualService cajaService;

    private MovimientoCajaVirtualRepository movRepo;
    private TesoreriaService tesoreriaService;
    private MovimientoCajaVirtualService movService;

    @BeforeEach
    void setUp() {
        cajaRepo = mock(CajaVirtualRepository.class);
        cajaService = new CajaVirtualService(cajaRepo);
        when(cajaRepo.filter(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        movRepo = mock(MovimientoCajaVirtualRepository.class);
        tesoreriaService = mock(TesoreriaService.class);
        movService = new MovimientoCajaVirtualService(movRepo, tesoreriaService);
        when(movRepo.filter(any(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
    }

    private final Pageable pageable = PageRequest.of(0, 10);

    @Test
    void filtro_caja_normaliza_nombre_vacio_a_null() {
        cajaService.filter("   ", CajaVirtualTipo.CAJA_MAYOR, 5L, true, pageable);

        ArgumentCaptor<String> nombre = ArgumentCaptor.forClass(String.class);
        // El repo recibe el tipo como String (name del enum) para evitar el 42P18 del enum nativo.
        verify(cajaRepo).filter(nombre.capture(), eq("CAJA_MAYOR"), eq(5L), eq(true), eq(pageable));
        assertNull(nombre.getValue(), "nombre en blanco debe pasarse como null (sin filtro)");
    }

    @Test
    void filtro_caja_trimea_nombre() {
        cajaService.filter("  Caja Mayor  ", null, null, null, pageable);

        ArgumentCaptor<String> nombre = ArgumentCaptor.forClass(String.class);
        verify(cajaRepo).filter(nombre.capture(), isNull(), isNull(), isNull(), eq(pageable));
        assertEquals("Caja Mayor", nombre.getValue());
    }

    @Test
    void filtro_movimientos_fecha_vacia_es_null() {
        movService.filter(1L, "", "", CajaVirtualTipoMovimiento.INGRESO, true, pageable);

        verify(movRepo).filter(eq(1L), isNull(), isNull(),
                eq("INGRESO"), eq(true), eq(pageable));
    }

    @Test
    void filtro_movimientos_convierte_fechas() {
        movService.filter(1L, "2026-07-01 00:00", "2026-07-31 23:59", null, false, pageable);

        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fin = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(movRepo).filter(eq(1L), desde.capture(), fin.capture(), isNull(), eq(false), eq(pageable));
        assertEquals(2026, desde.getValue().getYear());
        assertEquals(7, desde.getValue().getMonthValue());
        assertEquals(1, desde.getValue().getDayOfMonth());
        assertEquals(31, fin.getValue().getDayOfMonth());
    }
}
