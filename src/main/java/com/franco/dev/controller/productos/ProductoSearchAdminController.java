package com.franco.dev.controller.productos;

import com.franco.dev.service.productos.search.ProductoSearchIndexer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint administrativo para reindexar productos en Lucene.
 */
@RestController
@RequestMapping("/api/admin/productos")
public class ProductoSearchAdminController {

    private final ProductoSearchIndexer productoSearchIndexer;

    public ProductoSearchAdminController(ProductoSearchIndexer productoSearchIndexer) {
        this.productoSearchIndexer = productoSearchIndexer;
    }

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, String>> reindexar() {
        if (productoSearchIndexer.estaIndexando()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensaje", "Reindexación ya en curso"));
        }
        try {
            productoSearchIndexer.reindexarTodos();
            return ResponseEntity.ok(Map.of("mensaje", "Reindexación completada"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensaje", e.getMessage()));
        }
    }
}
