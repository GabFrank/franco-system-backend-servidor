package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Producto;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Búsqueda textual de productos vía Lucene (fuzzy, prefijo, orden libre, ranking).
 * Sin diccionarios ni reglas de negocio sobre el texto.
 */
@Service
public class ProductoSearchService {

    private static final float BOOST_PREFIJO = 8.0f;
    private static final float BOOST_FUZZY = 0.4f;
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
        return buscarProductosPorTexto(texto, maxResults, activo, isEnvase, familiaId, subfamiliaId).stream()
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
        String consulta = texto.trim();
        String[] tokens = consulta.split("\\s+");
        SearchSession session = Search.session(entityManager);

        SearchResult<Producto> result = session.search(Producto.class)
                .where(f -> {
                    var bool = f.bool();
                    aplicarFiltrosIndexados(bool, f, activo, isEnvase, familiaId, subfamiliaId);
                    for (String token : tokens) {
                        bool.must(crearPredicadoToken(f, token));
                    }
                    return bool;
                })
                .fetch(limit);

        return ordenarPorRelevancia(result.hits(), consulta);
    }

    private PredicateFinalStep crearPredicadoToken(SearchPredicateFactory f, String token) {
        return f.bool(tokenBool -> {
            tokenBool.should(f.wildcard()
                    .fields("descripcion", "descripcionFactura")
                    .matching(token + "*")
                    .boost(BOOST_PREFIJO));

            int fuzzyDistance = ProductoTextoRelevanceScorer.distanciaFuzzyMaxima(token.length());
            if (fuzzyDistance > 0) {
                tokenBool.should(f.match()
                        .fields("descripcion", "descripcionFactura")
                        .matching(token)
                        .fuzzy(fuzzyDistance)
                        .boost(BOOST_FUZZY));
            }

            tokenBool.minimumShouldMatchNumber(1);
        });
    }

    private List<Producto> ordenarPorRelevancia(List<Producto> productos, String consulta) {
        return productos.stream()
                .sorted(Comparator
                        .comparingInt((Producto producto) -> -ProductoTextoRelevanceScorer.puntuar(producto, consulta))
                        .thenComparing(Producto::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
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
