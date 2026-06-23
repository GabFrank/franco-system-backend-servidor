package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.repository.productos.CodigoRepository;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Búsqueda de códigos de barras por prefijo vía Lucene (o SQL como fallback).
 * Pensado para autocompletado mientras el usuario escribe.
 */
@Service
public class CodigoSearchService {

    private static final int MIN_PREFIJO = 3;

    @PersistenceContext
    private EntityManager entityManager;

    private final CodigoRepository codigoRepository;

    @Value("${app.search.producto.enabled:true}")
    private boolean productoSearchEnabled;

    public CodigoSearchService(CodigoRepository codigoRepository) {
        this.codigoRepository = codigoRepository;
    }

    public List<Long> buscarProductoIdsPorPrefijo(String prefijo, int maxResults) {
        if (prefijo == null || prefijo.trim().length() < MIN_PREFIJO) {
            return Collections.emptyList();
        }
        String normalizado = prefijo.trim().toUpperCase();
        int limit = maxResults > 0 ? maxResults : 50;

        if (productoSearchEnabled) {
            try {
                return buscarProductoIdsPorPrefijoLucene(normalizado, limit);
            } catch (Exception ignored) {
                // Fallback a SQL si el índice aún no está listo
            }
        }
        return codigoRepository.findProductoIdsByCodigoPrefijo(normalizado, limit);
    }

    private List<Long> buscarProductoIdsPorPrefijoLucene(String prefijo, int limit) {
        SearchSession session = Search.session(entityManager);
        SearchResult<Long> result = session.search(Codigo.class)
                .select(f -> f.field("productoId", Long.class))
                .where(f -> f.wildcard().field("codigo").matching(prefijo.toLowerCase() + "*"))
                .fetch(limit * 3);

        Set<Long> ids = new LinkedHashSet<>();
        for (Long productoId : result.hits()) {
            if (productoId != null) {
                ids.add(productoId);
                if (ids.size() >= limit) {
                    break;
                }
            }
        }
        return ids.stream().collect(Collectors.toList());
    }
}
