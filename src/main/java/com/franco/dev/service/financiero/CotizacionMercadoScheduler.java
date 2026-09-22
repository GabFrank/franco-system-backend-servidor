package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refresca cada N minutos la cotizacion de mercado del ultimo {@link Cambio} por moneda.
 *
 * <p><b>Es un adorno, no una dependencia.</b> Si nortecambios no responde, el sistema
 * sigue operando con la ultima cotizacion conocida: esta clase no propaga errores y no
 * retiene el hilo del scheduler.
 *
 * <p>Por que el executor propio: las 19 tareas {@code @Scheduled} del central comparten
 * un unico hilo (el default de Spring Boot es {@code spring.task.scheduling.pool.size=1}).
 * Esta es la unica que sale a un sitio de terceros en internet, asi que si se cuelga se
 * lleva puestas a todas las demas — entre ellas el poller que sube los retiros de la caja
 * del PDV a caja mayor y el dispatch de notificaciones. Corriendo en su propio hilo, el
 * peor caso de esta integracion es quedarse sin cotizacion de mercado.
 *
 * <p>Interruptor de corte operativo, sin deploy: {@code cotizacion.mercado.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "cotizacion.mercado.enabled", havingValue = "true", matchIfMissing = false)
public class CotizacionMercadoScheduler {

    private static final Logger log = LoggerFactory.getLogger(CotizacionMercadoScheduler.class);

    private final NorteCambiosScraper scraper;
    private final CambioService cambioService;
    private final MonedaService monedaService;

    /** Hilo propio (daemon) para no retener nunca el hilo compartido de @Scheduled. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cotizacion-mercado");
        t.setDaemon(true);
        return t;
    });

    /**
     * Una actualizacion a la vez. El executor de un solo hilo usa una cola ilimitada, asi que
     * sin este guard un tick que tarde mas que el fixedDelay (por ejemplo con el lado DB
     * trabado) encolaria los siguientes indefinidamente en vez de saltearlos. Y por cola
     * ilimitada el catch de RejectedExecutionException nunca se dispararia.
     */
    private final AtomicBoolean actualizando = new AtomicBoolean(false);

    public CotizacionMercadoScheduler(NorteCambiosScraper scraper, CambioService cambioService, MonedaService monedaService) {
        this.scraper = scraper;
        this.cambioService = cambioService;
        this.monedaService = monedaService;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Scheduled(
        fixedDelayString = "${cotizacion.mercado.fixed-delay:600000}",
        initialDelayString = "${cotizacion.mercado.initial-delay:60000}"
    )
    public void scheduledUpdate() {
        // Fire-and-forget: el hilo del scheduler vuelve de inmediato.
        if (!actualizando.compareAndSet(false, true)) {
            log.warn("CotizacionMercadoScheduler: la actualizacion anterior sigue corriendo; se saltea este tick");
            return;
        }
        try {
            executor.submit(this::ejecutarActualizacion);
        } catch (RejectedExecutionException e) {
            actualizando.set(false);
            log.warn("CotizacionMercadoScheduler: no se pudo encolar la actualizacion: {}", e.getMessage());
        }
    }

    private void ejecutarActualizacion() {
        log.info("Actualizacion automatica de cotizaciones de mercado");
        try {
            int count = actualizarCotizaciones();
            log.info("CotizacionMercadoScheduler: {} monedas actualizadas", count);
        } catch (Exception e) {
            log.warn("CotizacionMercadoScheduler: error en actualizacion automatica: {}", e.getMessage());
        } finally {
            actualizando.set(false);
        }
    }

    /**
     * Actualiza la cotizacion de mercado de las monedas que nortecambios haya devuelto.
     *
     * @return cuantas monedas se actualizaron; 0 si no se pudo obtener nada. Nunca lanza
     *         por falta de cotizaciones: no poder leer el mercado no es un error del sistema.
     */
    public int actualizarCotizaciones() {
        Map<String, double[]> rates = scraper.fetchRates();
        if (rates.isEmpty()) {
            log.warn("CotizacionMercadoScheduler: sin cotizaciones de nortecambios.com.py; se conserva la ultima conocida");
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, double[]> entry : rates.entrySet()) {
            String monedaKey = entry.getKey();
            double[] rateValues = entry.getValue();
            try {
                Moneda moneda = monedaService.findByDescripcion(monedaKey);
                if (moneda == null) {
                    log.warn("CotizacionMercadoScheduler: moneda no encontrada: {}", monedaKey);
                    continue;
                }
                Cambio ultimo = cambioService.findLastByMonedaId(moneda.getId());
                if (ultimo == null) {
                    log.warn("CotizacionMercadoScheduler: no existe Cambio previo para {}", monedaKey);
                    continue;
                }
                ultimo.setValorEnGsVentaMercado(rateValues[0]);
                ultimo.setValorEnGsCompraMercado(rateValues[1]);
                cambioService.save(ultimo);
                count++;
            } catch (Exception e) {
                log.warn("CotizacionMercadoScheduler: error actualizando {}: {}", monedaKey, e.getMessage());
            }
        }
        return count;
    }
}
