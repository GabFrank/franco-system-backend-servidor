package com.franco.dev.config.search;

import com.franco.dev.service.productos.search.ProductoSearchIndexEstadoService;
import com.franco.dev.service.productos.search.ProductoSearchIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reindexa productos y códigos automáticamente al arrancar cuando falta algún índice Lucene.
 * No requiere ejecutar curl ni endpoints manuales.
 */
@Component
@ConditionalOnProperty(name = "app.search.producto.enabled", havingValue = "true", matchIfMissing = true)
public class ProductoSearchStartupIndexer {

    private static final Logger log = LoggerFactory.getLogger(ProductoSearchStartupIndexer.class);

    private final ProductoSearchIndexer productoSearchIndexer;
    private final ProductoSearchIndexEstadoService productoSearchIndexEstadoService;

    @Value("${app.search.producto.auto-reindex-if-empty:true}")
    private boolean autoReindexIfEmpty;

    @Value("${app.search.producto.reindex-on-startup:false}")
    private boolean reindexOnStartup;

    public ProductoSearchStartupIndexer(
            ProductoSearchIndexer productoSearchIndexer,
            ProductoSearchIndexEstadoService productoSearchIndexEstadoService) {
        this.productoSearchIndexer = productoSearchIndexer;
        this.productoSearchIndexEstadoService = productoSearchIndexEstadoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        boolean forzarReindex = reindexOnStartup;
        boolean requiereReindex = productoSearchIndexEstadoService.requiereReindexacionAutomatica();
        boolean soloCodigos = productoSearchIndexEstadoService.requiereReindexacionSoloCodigos();

        if (!forzarReindex && !(autoReindexIfEmpty && requiereReindex)) {
            return;
        }

        if (productoSearchIndexer.estaIndexando()) {
            log.info("Reindexación Lucene ya en curso, se omite el arranque automático.");
            return;
        }

        try {
            if (forzarReindex) {
                log.info("Reindexación completa forzada al arrancar (productos + códigos)...");
                productoSearchIndexer.reindexarTodos();
            } else if (soloCodigos) {
                log.info("Índice Lucene de códigos ausente. Reindexando códigos automáticamente...");
                productoSearchIndexer.reindexarCodigos();
            } else {
                log.info("Índice Lucene incompleto. Reindexando productos y códigos automáticamente...");
                productoSearchIndexer.reindexarTodos();
            }
            log.info("Reindexación automática Lucene finalizada.");
        } catch (Exception e) {
            log.error("Error en reindexación automática Lucene al arrancar", e);
        }
    }
}
