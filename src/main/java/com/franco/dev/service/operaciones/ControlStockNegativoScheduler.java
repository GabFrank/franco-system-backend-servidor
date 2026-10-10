package com.franco.dev.service.operaciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Evalua las ventas que llegan replicadas de las filiales y registra en el control las que
 * salieron con stock 0 o negativo.
 *
 * Hilo propio: las tareas {@code @Scheduled} del central comparten un unico hilo (ver
 * {@code CotizacionMercadoScheduler}). Cuando una filial vuelve de estar offline este ciclo puede
 * durar decenas de segundos; corriendo en el hilo compartido frenaria la replicacion, el poller de
 * retiros y las notificaciones. El {@code @Scheduled} solo encola.
 *
 * Encendido por defecto; se apaga con
 * {@code inventario.control-stock-negativo.poller.enabled=false} (env
 * {@code INVENTARIO_CONTROL_STOCK_NEGATIVO_ENABLED}). Apagado en los perfiles dev y ci.
 */
@Component
@ConditionalOnProperty(name = "inventario.control-stock-negativo.poller.enabled",
        havingValue = "true", matchIfMissing = true)
public class ControlStockNegativoScheduler {

    private static final Logger log = LoggerFactory.getLogger(ControlStockNegativoScheduler.class);

    /** Tope de un ciclo. Lo que no entra se procesa en el siguiente, empezando por lo mas atrasado. */
    static final long PRESUPUESTO_MS = 20_000;

    private final ControlStockNegativoProcesador procesador;
    private final LongSupplier relojMs;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "control-stock-negativo");
        t.setDaemon(true);
        return t;
    });

    /** Un ciclo a la vez: la cola del executor es ilimitada y sin esto los ticks se apilarian. */
    private final AtomicBoolean corriendo = new AtomicBoolean(false);

    @Autowired
    public ControlStockNegativoScheduler(ControlStockNegativoProcesador procesador) {
        this(procesador, System::currentTimeMillis);
    }

    ControlStockNegativoScheduler(ControlStockNegativoProcesador procesador, LongSupplier relojMs) {
        this.procesador = procesador;
        this.relojMs = relojMs;
    }

    @Scheduled(
            fixedDelayString = "${inventario.control-stock-negativo.poller.fixed-delay:60000}",
            initialDelayString = "${inventario.control-stock-negativo.poller.initial-delay:120000}"
    )
    public void evaluarVentas() {
        if (!corriendo.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.submit(() -> {
                try {
                    ciclo();
                } finally {
                    corriendo.set(false);
                }
            });
        } catch (RuntimeException e) {
            corriendo.set(false);
            log.warn("ControlStockNegativoScheduler: no se pudo encolar el ciclo: {}", e.getMessage());
        }
    }

    /** Un ciclo completo, sincrono. Nunca propaga una excepcion. */
    void ciclo() {
        try {
            long inicio = relojMs.getAsLong();
            int total = 0;
            for (Long sucursalId : procesador.sucursales()) {
                if (relojMs.getAsLong() - inicio > PRESUPUESTO_MS) {
                    log.info("ControlStockNegativoScheduler: ciclo cortado por tiempo, sigue en el proximo");
                    break;
                }
                try {
                    total += procesador.procesarSucursal(sucursalId);
                } catch (Exception e) {
                    log.warn("ControlStockNegativoScheduler: sucursal {} no procesada: {}",
                            sucursalId, e.getMessage());
                }
            }
            if (total > 0) log.info("ControlStockNegativoScheduler: {} ventas registradas", total);
        } catch (Exception e) {
            log.warn("ControlStockNegativoScheduler: error en el ciclo: {}", e.getMessage());
        }
    }
}
