package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * La cotizacion de mercado de nortecambios es un adorno: si no carga, nadie se entera.
 * Estos tests fijan esa propiedad, que es justamente la que se rompio en produccion.
 *
 * <p>Ninguno sale a la red: el presupuesto de tiempo se ejercita sobreescribiendo
 * {@code doFetchRates()}, no pegandole al sitio real. Un test que depende de la
 * conectividad del runner de CI no prueba nada y ademas es flaky.
 */
class CotizacionMercadoNoBloqueaTest {

    /** Scraper cuyo scrape real nunca termina a tiempo, sin tocar la red. */
    private static NorteCambiosScraper scraperQueSeCuelga(long timeoutMs, long duracionScrapeMs,
                                                          AtomicBoolean scrapeTermino) {
        return new NorteCambiosScraper(new ObjectMapper(), timeoutMs) {
            @Override
            Map<String, double[]> doFetchRates() {
                try {
                    TimeUnit.MILLISECONDS.sleep(duracionScrapeMs);
                    scrapeTermino.set(true);
                    return Collections.emptyMap();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Collections.emptyMap();
                }
            }
        };
    }

    @Test
    @DisplayName("fetchRates corta por presupuesto y devuelve vacio, sin colgar al llamador")
    void fetchRatesCortaPorPresupuesto() {
        AtomicBoolean scrapeTermino = new AtomicBoolean(false);
        NorteCambiosScraper scraper = scraperQueSeCuelga(200L, 10_000L, scrapeTermino);

        long inicio = System.currentTimeMillis();
        Map<String, double[]> rates = scraper.fetchRates();
        long transcurrido = System.currentTimeMillis() - inicio;

        assertNotNull(rates, "fetchRates nunca devuelve null");
        assertTrue(rates.isEmpty(), "sin cotizaciones, mapa vacio");
        assertFalse(scrapeTermino.get(), "el scrape seguia corriendo: el corte fue por presupuesto");
        assertTrue(transcurrido < 3000,
                "fetchRates retuvo al llamador " + transcurrido + " ms con un presupuesto de 200 ms");
    }

    @Test
    @DisplayName("con un scrape clavado, el intento siguiente se saltea en vez de encolarse")
    void scrapeClavadoNoAcumulaIntentos() {
        NorteCambiosScraper scraper = scraperQueSeCuelga(200L, 10_000L, new AtomicBoolean(false));
        scraper.fetchRates(); // queda uno en vuelo, clavado

        long inicio = System.currentTimeMillis();
        Map<String, double[]> segundo = scraper.fetchRates();
        long transcurrido = System.currentTimeMillis() - inicio;

        assertTrue(segundo.isEmpty(), "el intento salteado devuelve vacio");
        assertTrue(transcurrido < 100,
                "el segundo intento tardo " + transcurrido + " ms: deberia salir de inmediato por el guard");
    }

    @Test
    @DisplayName("sin cotizaciones, actualizarCotizaciones devuelve 0 en vez de lanzar")
    void sinCotizacionesNoLanza() {
        NorteCambiosScraper scraper = mock(NorteCambiosScraper.class);
        when(scraper.fetchRates()).thenReturn(Collections.emptyMap());

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
            TimeUnit.SECONDS.sleep(3); // simula un nortecambios que no responde
            return Collections.emptyMap();
        });

        CotizacionMercadoScheduler scheduler =
                new CotizacionMercadoScheduler(scraper, mock(CambioService.class), mock(MonedaService.class));

        long inicio = System.currentTimeMillis();
        scheduler.scheduledUpdate();
        long transcurrido = System.currentTimeMillis() - inicio;

        assertTrue(transcurrido < 1000,
                "scheduledUpdate retuvo el hilo del scheduler " + transcurrido + " ms; debe delegar y volver");
    }

    @Test
    @DisplayName("un tick no se encola detras del anterior si el anterior sigue corriendo")
    void tickSolapadoSeSaltea() throws Exception {
        NorteCambiosScraper scraper = mock(NorteCambiosScraper.class);
        when(scraper.fetchRates()).thenAnswer(inv -> {
            TimeUnit.SECONDS.sleep(2);
            return Collections.emptyMap();
        });

        CotizacionMercadoScheduler scheduler =
                new CotizacionMercadoScheduler(scraper, mock(CambioService.class), mock(MonedaService.class));

        scheduler.scheduledUpdate();
        TimeUnit.MILLISECONDS.sleep(200); // el primero ya arranco
        scheduler.scheduledUpdate();
        scheduler.scheduledUpdate();
        TimeUnit.MILLISECONDS.sleep(2500); // el primero ya termino

        verify(scraper, times(1)).fetchRates();
    }
}
