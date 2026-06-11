package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Producto;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Búsqueda textual de productos vía Lucene (fuzzy, prefijo, orden libre, ranking).
 * Sin diccionarios ni reglas de negocio sobre el texto.
 */
@Service
public class ProductoSearchService {

    private static final int FUZZY_MAX_EDIT_DISTANCE = 2;
    private static final int DEFAULT_MAX_RESULTS = 50;

    @PersistenceContext
    private EntityManager entityManager;

    public boolean textoBusquedaValido(String texto) {
        return texto != null && !texto.trim().isEmpty();
    }

    public List<Long> buscarIdsPorTexto(String texto, int maxResults) {
        return buscarIdsPorTexto(texto, maxResults, null, null, null, null);
    }

    public List<Long> buscarIdsPorTexto(
            String texto,
            int maxResults,
            Boolean activo,
            Boolean isEnvase,
            Long familiaId,
            Long subfamiliaId) {
        if (!textoBusquedaValido(texto)) {
            return Collections.emptyList();
        }
        int limit = maxResults > 0 ? maxResults : DEFAULT_MAX_RESULTS;
        String[] tokens = texto.trim().split("\\s+");
        SearchSession session = Search.session(entityManager);

        SearchResult<Producto> result = session.search(Producto.class)
                .where(f -> {
                    var bool = f.bool();
                    aplicarFiltrosIndexados(bool, f, activo, isEnvase, familiaId, subfamiliaId);
                    for (String token : tokens) {
                        bool.must(f.bool(tokenBool -> tokenBool
                                .should(f.match()
                                        .fields("descripcion", "descripcionFactura")
                                        .matching(token)
                                        .fuzzy(FUZZY_MAX_EDIT_DISTANCE))
                                .should(f.wildcard()
                                        .fields("descripcion", "descripcionFactura")
                                        .matching(token + "*"))
                                .minimumShouldMatchNumber(1)));
                    }
                    return bool;
                })
                .fetch(limit);

        return result.hits().stream()
                .map(Producto::getId)
                .collect(Collectors.toList());
    }

    public List<Producto> buscarProductosPorTexto(
            String texto,
            int maxResults,
            Boolean activo,
            Boolean isEnvase,
            Long familiaId,
            Long subfamiliaId) {
        if (!textoBusquedaValido(texto)) {
            return Collections.emptyList();
        }
        int limit = maxResults > 0 ? maxResults : DEFAULT_MAX_RESULTS;
        String[] tokens = texto.trim().split("\\s+");
        SearchSession session = Search.session(entityManager);

        return session.search(Producto.class)
                .where(f -> {
                    var bool = f.bool();
                    aplicarFiltrosIndexados(bool, f, activo, isEnvase, familiaId, subfamiliaId);
                    for (String token : tokens) {
                        bool.must(f.bool(tokenBool -> tokenBool
                                .should(f.match()
                                        .fields("descripcion", "descripcionFactura")
                                        .matching(token)
                                        .fuzzy(FUZZY_MAX_EDIT_DISTANCE))
                                .should(f.wildcard()
                                        .fields("descripcion", "descripcionFactura")
                                        .matching(token + "*"))
                                .minimumShouldMatchNumber(1)));
                    }
                    return bool;
                })
                .fetch(limit)
                .hits();
    }

    private void aplicarFiltrosIndexados(
            org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep<?> bool,
            org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory f,
            Boolean activo,
            Boolean isEnvase,
            Long familiaId,
            Long subfamiliaId) {
        if (activo != null) {
            bool.must(f.match().field("activo").matching(activo));
        }
        if (Boolean.TRUE.equals(isEnvase)) {
            bool.must(f.match().field("isEnvase").matching(true));
        }
        if (familiaId != null) {
            bool.must(f.match().field("subfamilia.familia.id").matching(familiaId));
        }
        if (subfamiliaId != null) {
            bool.must(f.match().field("subfamilia.id").matching(subfamiliaId));
        }
    }
}
