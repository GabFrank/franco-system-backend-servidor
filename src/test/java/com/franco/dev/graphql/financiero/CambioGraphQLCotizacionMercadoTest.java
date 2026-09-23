package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.NorteCambiosScraper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@code actualizarCotizacionesMercado} es una lectura best-effort de un sitio de terceros:
 * nunca lanza, y respeta el mismo interruptor que apaga al scheduler.
 */
class CambioGraphQLCotizacionMercadoTest {

    private NorteCambiosScraper scraper;
    private CambioService cambioService;
    private MonedaService monedaService;
    private CambioGraphQL resolver;

    @BeforeEach
    void setUp() {
        scraper = mock(NorteCambiosScraper.class);
        cambioService = mock(CambioService.class);
        monedaService = mock(MonedaService.class);
        resolver = new CambioGraphQL();
        ReflectionTestUtils.setField(resolver, "norteCambiosScraper", scraper);
        ReflectionTestUtils.setField(resolver, "service", cambioService);
        ReflectionTestUtils.setField(resolver, "monedaService", monedaService);
        ReflectionTestUtils.setField(resolver, "cotizacionMercadoHabilitada", true);
    }

    @Test
    @DisplayName("con el interruptor apagado no sale a internet y devuelve false")
    void interruptorApagadoNoTocaLaRed() {
        ReflectionTestUtils.setField(resolver, "cotizacionMercadoHabilitada", false);

        assertFalse(resolver.actualizarCotizacionesMercado());
        verify(scraper, never()).fetchRates();
    }

    @Test
    @DisplayName("sin cotizaciones devuelve false en vez de lanzar")
    void sinCotizacionesDevuelveFalse() {
        when(scraper.fetchRates()).thenReturn(Collections.emptyMap());

        assertFalse(resolver.actualizarCotizacionesMercado(),
                "nortecambios caido no puede abortar la operacion del cliente que lo pidio");
    }

    @Test
    @DisplayName("si el scraper explota, devuelve false en vez de propagar")
    void scraperQueExplotaNoPropaga() {
        when(scraper.fetchRates()).thenThrow(new RuntimeException("boom"));

        assertFalse(resolver.actualizarCotizacionesMercado());
    }

    @Test
    @DisplayName("una moneda que falla no arrastra a las demas")
    void unaMonedaQueFallaNoArrastra() {
        when(scraper.fetchRates()).thenReturn(new java.util.LinkedHashMap<String, double[]>() {{
            put("DOLAR", new double[]{7000d, 7200d});
            put("REAL", new double[]{1200d, 1300d});
        }});
        when(monedaService.findByDescripcion("DOLAR")).thenThrow(new RuntimeException("boom"));

        Moneda real = new Moneda();
        real.setId(2L);
        Cambio ultimoReal = new Cambio();
        when(monedaService.findByDescripcion("REAL")).thenReturn(real);
        when(cambioService.findLastByMonedaId(2L)).thenReturn(ultimoReal);

        assertTrue(resolver.actualizarCotizacionesMercado(),
                "el REAL se actualizo aunque el DOLAR fallo");
        assertEquals(Double.valueOf(1200d), ultimoReal.getValorEnGsVentaMercado());
        assertEquals(Double.valueOf(1300d), ultimoReal.getValorEnGsCompraMercado());
        verify(cambioService, times(1)).save(ultimoReal);
    }
}
