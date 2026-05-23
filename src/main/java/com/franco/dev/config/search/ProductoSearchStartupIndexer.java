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
 * Reindexa productos automáticamente al arrancar si el índice Lucene está vacío.
 * No requiere ejecutar curl manual en el primer uso.
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
        boolean indiceVacio = productoSearchIndexEstadoService.indiceVacioONoExiste();

        if (!forzarReindex && !(autoReindexIfEmpty && indiceVacio)) {
            return;
        }

        if (productoSearchIndexer.estaIndexando()) {
            log.info("Reindexación de productos ya en curso, se omite el arranque automático.");
            return;
        }

        log.info("Índice Lucene de productos vacío o inexistente. Iniciando reindexación automática...");
        try {
            productoSearchIndexer.reindexarTodos();
            log.info("Reindexación automática de productos finalizada.");
        } catch (Exception e) {
            log.error("Error en reindexación automática de productos al arrancar", e);
        }
    }
}
