package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * La cotizacion de mercado de nortecambios es un adorno: si no carga, nadie se entera.
 * Estos tests fijan esa propiedad, que es justamente la que se rompio en produccion.
 */
class CotizacionMercadoNoBloqueaTest {

    @Test
    @DisplayName("fetchRates corta por presupuesto y devuelve vacio, nunca lanza ni cuelga")
    void fetchRatesNoSeCuelga() {
        // Presupuesto de 200 ms. En CI no hay salida a nortecambios: el scrape falla
        // rapido o se corta por presupuesto, pero en ningun caso retiene al llamador.
        NorteCambiosScraper scraper = new NorteCambiosScraper(new ObjectMapper(), 200L);

        long inicio = System.currentTimeMillis();
        Map<String, double[]> rates = scraper.fetchRates();
        long transcurrido = System.currentTimeMillis() - inicio;

        assertNotNull(rates, "fetchRates nunca devuelve null");
        assertTrue(transcurrido < 5000,
                "fetchRates retuvo al llamador " + transcurrido + " ms con un presupuesto de 200 ms");
    }

    @Test
    @DisplayName("sin cotizaciones, actualizarCotizaciones devuelve 0 en vez de lanzar")
    void sinCotizacionesNoLanza() {
        NorteCambiosScraper scraper = mock(NorteCambiosScraper.class);
        when(scraper.fetchRates()).thenReturn(java.util.Collections.emptyMap());

        CotizacionMercadoScheduler scheduler =
                new CotizacionMercadoScheduler(scraper, mock(CambioService.class), mock(MonedaService.class));

        assertEquals(0, scheduler.actualizarCotizaciones(),
                "nortecambios caido no es un error del sistema: devuelve 0, no excepcion");
    }

    @Test
    @DisplayName("el tick del scheduler no retiene el hilo compartido de @Scheduled")
    void scheduledUpdateNoRetieneElHilo() {
        NorteCambiosScraper scraper = mock(NorteCambiosScraper.class);
        when(scraper.fetchRates()).thenAnswer(inv -> {
            // Simula un nortecambios que no responde.
            TimeUnit.SECONDS.sleep(3);
            return java.util.Collections.emptyMap();
        });

        CotizacionMercadoScheduler scheduler =
                new CotizacionMercadoScheduler(scraper, mock(CambioService.class), mock(MonedaService.class));

        long inicio = System.currentTimeMillis();
        scheduler.scheduledUpdate();
        long transcurrido = System.currentTimeMillis() - inicio;

        assertTrue(transcurrido < 1000,
                "scheduledUpdate retuvo el hilo del scheduler " + transcurrido + " ms; debe delegar y volver");
    }
}
