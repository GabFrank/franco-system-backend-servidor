package com.franco.dev.service.financiero;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispara {@link FacturaCorreoService#enviarPendientes()} cada {@code factura.correo.fixed-delay}.
 *
 * <p>Hilo propio, igual que {@link CotizacionMercadoScheduler}: las tareas {@code @Scheduled} del
 * central comparten un unico hilo y esta sale a un servidor de correo externo. Si el SMTP se
 * cuelga, lo unico que se atrasa son los correos.
 *
 * <p>Interruptor: {@code factura.correo.enabled} (apagado por defecto).
 */
@Component
@ConditionalOnProperty(name = "factura.correo.enabled", havingValue = "true", matchIfMissing = false)
public class FacturaCorreoScheduler {

    private static final Logger log = LoggerFactory.getLogger(FacturaCorreoScheduler.class);

    private final FacturaCorreoService facturaCorreoService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "factura-correo");
        t.setDaemon(true);
        return t;
    });

    /** Una vuelta a la vez: si la anterior sigue enviando, este tick se saltea en vez de encolarse. */
    private final AtomicBoolean enviando = new AtomicBoolean(false);

    public FacturaCorreoScheduler(FacturaCorreoService facturaCorreoService) {
        this.facturaCorreoService = facturaCorreoService;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Scheduled(
        fixedDelayString = "${factura.correo.fixed-delay:60000}",
        initialDelayString = "${factura.correo.initial-delay:90000}"
    )
    public void enviarPendientes() {
        if (!enviando.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.submit(this::ejecutar);
        } catch (RuntimeException | Error t) {
            // La tarea nunca corrio, asi que su finally tampoco: sin esto el guard queda trabado.
            enviando.set(false);
            throw t;
        }
    }

    private void ejecutar() {
        try {
            facturaCorreoService.enviarPendientes();
        } catch (Exception e) {
            log.error("Correo de facturas: fallo la vuelta de envio: {}", e.getMessage(), e);
        } finally {
            enviando.set(false);
        }
    }
}
