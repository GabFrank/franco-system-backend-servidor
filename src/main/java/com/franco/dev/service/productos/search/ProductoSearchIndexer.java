package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Producto;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reindexación masiva de productos en Lucene.
 * No modifica la lógica de búsqueda SQL existente; solo construye/actualiza el índice.
 */
@Service
public class ProductoSearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(ProductoSearchIndexer.class);
    private static final int MASS_INDEXER_THREADS = 4;

    private final EntityManagerFactory entityManagerFactory;
    private final AtomicBoolean indexing = new AtomicBoolean(false);

    public ProductoSearchIndexer(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public boolean estaIndexando() {
        return indexing.get();
    }

    public void reindexarTodos() {
        reindexar(Producto.class, Codigo.class);
    }

    public void reindexarCodigos() {
        reindexar(Codigo.class);
    }

    @SafeVarargs
    private final void reindexar(Class<?>... entidades) {
        if (!indexing.compareAndSet(false, true)) {
            throw new IllegalStateException("Ya hay una reindexación en curso");
        }
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            log.info("Iniciando reindexación masiva en Lucene para: {}", java.util.Arrays.toString(entidades));
            SearchSession searchSession = Search.session(entityManager);
            for (Class<?> entidad : entidades) {
                searchSession.massIndexer(entidad)
                        .threadsToLoadObjects(MASS_INDEXER_THREADS)
                        .startAndWait();
            }
            log.info("Reindexación masiva finalizada para: {}", java.util.Arrays.toString(entidades));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reindexación interrumpida", e);
        } finally {
            entityManager.close();
            indexing.set(false);
        }
    }
}
