package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final List<NorteCambiosScraper> creados = new ArrayList<>();

    @AfterEach
    void apagarPools() {
        creados.forEach(NorteCambiosScraper::shutdown);
    }

    /**
     * Scraper cuyo scrape se traba hasta que se suelta el latch, **ignorando la interrupcion**.
     * Imita el caso que motiva todo el diseño: una resolucion DNS nativa, que {@code cancel(true)}
     * no puede desbloquear. Nunca toca la red.
     */
    private NorteCambiosScraper scraperClavado(long timeoutMs, CountDownLatch soltar,
                                               AtomicInteger invocaciones) {
        NorteCambiosScraper scraper = new NorteCambiosScraper(new ObjectMapper(), timeoutMs) {
            @Override
            Map<String, double[]> doFetchRates() {
                invocaciones.incrementAndGet();
                while (soltar.getCount() > 0) {
                    try {
                        soltar.await();
                    } catch (InterruptedException e) {
                        // A proposito: este scrape no es interrumpible, como el DNS real.
                    }
                }
                return Collections.emptyMap();
            }
        };
        creados.add(scraper);
        return scraper;
    }

    @Test
    @DisplayName("fetchRates corta por presupuesto y devuelve vacio, sin colgar al llamador")
    void fetchRatesCortaPorPresupuesto() {
        CountDownLatch soltar = new CountDownLatch(1);
        AtomicInteger invocaciones = new AtomicInteger();
        NorteCambiosScraper scraper = scraperClavado(200L, soltar, invocaciones);

        long inicio = System.currentTimeMillis();
        Map<String, double[]> rates = scraper.fetchRates();
        long transcurrido = System.currentTimeMillis() - inicio;

        assertNotNull(rates, "fetchRates nunca devuelve null");
        assertTrue(rates.isEmpty(), "sin cotizaciones, mapa vacio");
        assertTrue(transcurrido < 3000,
                "fetchRates retuvo al llamador " + transcurrido + " ms con un presupuesto de 200 ms");
        soltar.countDown();
    }

    @Test
    @DisplayName("con un scrape clavado el intento siguiente se saltea, y al soltarse se reintenta")
    void scrapeClavadoSeSalteaYLuegoSeRecupera() throws Exception {
        CountDownLatch soltar = new CountDownLatch(1);
        AtomicInteger invocaciones = new AtomicInteger();
        // Presupuesto holgado: un intento real tardaria 5 s, uno salteado vuelve de inmediato.
        // Asi el assert distingue las dos cosas sin depender de milisegundos finos.
        NorteCambiosScraper scraper = scraperClavado(5_000L, soltar, invocaciones);

        scraper.fetchRates(); // queda uno clavado, y agota su presupuesto

        long inicio = System.currentTimeMillis();
        Map<String, double[]> segundo = scraper.fetchRates();
        long transcurrido = System.currentTimeMillis() - inicio;

        assertTrue(segundo.isEmpty(), "el intento salteado devuelve vacio");
        assertTrue(transcurrido < 1000,
                "el segundo intento tardo " + transcurrido + " ms: deberia salir por el guard, no reintentar");
        assertEquals(1, invocaciones.get(), "el segundo intento no debe haber entrado al scrape");

        // Al soltarse el scrape clavado, el guard se libera y el scraper vuelve a intentar.
        // Es lo que el test anterior no probaba: que el guard no quede trabado para siempre.
        soltar.countDown();
        long limite = System.currentTimeMillis() + 5_000;
        while (invocaciones.get() < 2 && System.currentTimeMillis() < limite) {
            scraper.fetchRates();
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertEquals(2, invocaciones.get(),
                "tras soltarse el scrape clavado, el guard debe permitir un intento nuevo");
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
