package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Producto;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Búsqueda textual de productos vía Lucene (fuzzy, prefijo, orden libre, ranking).
 * Sin diccionarios ni reglas de negocio sobre el texto.
 *
 * <p>Trabaja en dos pasadas. La primera es estricta (prefijo + subcadena) y es la
 * que responde la enorme mayoría de las búsquedas. Sólo si esa pasada no devuelve
 * nada se corre una segunda pasada tolerante que agrega subsecuencia, fuzzy,
 * token partido y coincidencia parcial de palabras. De esa forma el ruido de la
 * búsqueda flexible nunca contamina un resultado que ya era bueno: aparece
 * únicamente cuando la alternativa era "Producto no encontrado".
 */
@Service
public class ProductoSearchService {

    private static final String[] CAMPOS = { "descripcion", "descripcionFactura" };

    private static final float BOOST_PREFIJO = 8.0f;
    private static final float BOOST_INFIJO = 2.0f;
    private static final float BOOST_PARTIDO = 1.0f;
    private static final float BOOST_SUBSECUENCIA = 0.6f;
    private static final float BOOST_FUZZY = 0.4f;
    private static final int DEFAULT_MAX_RESULTS = 50;

    /**
     * Longitud minima para habilitar el wildcard de subcadena. Con 1 caracter el
     * patron `*x*` matchea practicamente todo el catalogo y deja de ser util.
     */
    static final int MIN_LONGITUD_INFIJO = 2;

    /** Debajo de 3 caracteres la subsecuencia `a*b*` no discrimina nada. */
    static final int MIN_LONGITUD_SUBSECUENCIA = 3;

    /** Para partir un token pegado ("cocacola") hacen falta 2 mitades utiles. */
    static final int MIN_LONGITUD_PARTE = 3;
    static final int MIN_LONGITUD_TOKEN_PARTIDO = MIN_LONGITUD_PARTE * 2;

    private enum Modo {
        ESTRICTA, TOLERANTE
    }

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

        List<Producto> hits = ejecutar(session, tokens, limit, Modo.ESTRICTA, activo, isEnvase, familiaId,
                subfamiliaId);
        if (hits.isEmpty()) {
            hits = ejecutar(session, tokens, limit, Modo.TOLERANTE, activo, isEnvase, familiaId, subfamiliaId);
        }

        return ordenarPorRelevancia(hits, consulta);
    }

    private List<Producto> ejecutar(
            SearchSession session,
            String[] tokens,
            int limit,
            Modo modo,
            Boolean activo,
            Boolean isEnvase,
            Long familiaId,
            Long subfamiliaId) {
        return session.search(Producto.class)
                .where(f -> {
                    var bool = f.bool();
                    aplicarFiltrosIndexados(bool, f, activo, isEnvase, familiaId, subfamiliaId);
                    aplicarTokens(bool, f, tokens, modo);
                    return bool;
                })
                .fetch(limit)
                .hits();
    }

    private void aplicarTokens(
            BooleanPredicateClausesStep<?> bool,
            SearchPredicateFactory f,
            String[] tokens,
            Modo modo) {
        if (modo == Modo.ESTRICTA || tokens.length < 2) {
            for (String token : tokens) {
                bool.must(crearPredicadoToken(f, token, modo));
            }
            return;
        }
        // Tolerante y multi-palabra: se permite que una palabra quede sin matchear,
        // asi "coca colaa mal tipeada" no devuelve cero por culpa de un solo termino.
        bool.must(f.bool(tokenBool -> {
            for (String token : tokens) {
                tokenBool.should(crearPredicadoToken(f, token, modo));
            }
            tokenBool.minimumShouldMatchNumber(Math.max(1, tokens.length - 1));
        }));
    }

    private PredicateFinalStep crearPredicadoToken(SearchPredicateFactory f, String token, Modo modo) {
        // wildcard() no pasa el patron por el analyzer del campo (a diferencia de match()),
        // por lo que hay que normalizarlo a mano para que coincida con el indice (lowercase + sin tildes).
        String base = ProductoTextoRelevanceScorer.normalizar(token);
        String patron = escaparWildcard(base);

        return f.bool(tokenBool -> {
            tokenBool.should(f.wildcard()
                    .fields(CAMPOS)
                    .matching(patron + "*")
                    .boost(BOOST_PREFIJO));

            // Subcadena: cubre escribir un fragmento del medio de la palabra
            // o comerse las primeras letras ("onti" -> "CONTI").
            if (base.length() >= MIN_LONGITUD_INFIJO) {
                tokenBool.should(f.wildcard()
                        .fields(CAMPOS)
                        .matching("*" + patron + "*")
                        .boost(BOOST_INFIJO));
            }

            if (modo == Modo.TOLERANTE) {
                agregarPredicadosTolerantes(tokenBool, f, token, base);
            }

            tokenBool.minimumShouldMatchNumber(1);
        });
    }

    private void agregarPredicadosTolerantes(
            BooleanPredicateClausesStep<?> tokenBool,
            SearchPredicateFactory f,
            String token,
            String base) {
        // Subsecuencia: omitir letras preserva el orden de las que quedan, asi que
        // "cnti"/"coa"/"piln" siguen siendo subsecuencia de CONTI/COCA/PILSEN.
        // Cubre cualquier cantidad de letras faltantes, en cualquier posicion.
        if (base.length() >= MIN_LONGITUD_SUBSECUENCIA) {
            tokenBool.should(f.wildcard()
                    .fields(CAMPOS)
                    .matching(patronSubsecuencia(base))
                    .boost(BOOST_SUBSECUENCIA));
        }

        // Fuzzy: cubre la otra familia de errores, letra cambiada o de mas.
        int fuzzyDistance = ProductoTextoRelevanceScorer.distanciaFuzzyMaxima(base.length());
        if (fuzzyDistance > 0) {
            tokenBool.should(f.match()
                    .fields(CAMPOS)
                    .matching(token)
                    .fuzzy(fuzzyDistance)
                    .boost(BOOST_FUZZY));
        }

        // Token partido: "cocacola" -> COCA + COLA, dos terminos distintos del indice.
        if (base.length() >= MIN_LONGITUD_TOKEN_PARTIDO) {
            for (int corte = MIN_LONGITUD_PARTE; corte <= base.length() - MIN_LONGITUD_PARTE; corte++) {
                String izquierda = escaparWildcard(base.substring(0, corte));
                String derecha = escaparWildcard(base.substring(corte));
                tokenBool.should(f.bool()
                        .must(f.wildcard().fields(CAMPOS).matching(izquierda + "*"))
                        .must(f.wildcard().fields(CAMPOS).matching(derecha + "*"))
                        .boost(BOOST_PARTIDO));
            }
        }
    }

    /** Construye `c*o*a*`: cada letra del token, en orden, con huecos entre medio. */
    static String patronSubsecuencia(String base) {
        StringBuilder patron = new StringBuilder(base.length() * 2);
        for (int i = 0; i < base.length(); i++) {
            char caracter = base.charAt(i);
            if (esMetacaracterWildcard(caracter)) {
                patron.append('\\');
            }
            patron.append(caracter).append('*');
        }
        return patron.toString();
    }

    /** Evita que un `*` o `?` tipeado por el usuario se interprete como comodin. */
    static String escaparWildcard(String texto) {
        StringBuilder escapado = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char caracter = texto.charAt(i);
            if (esMetacaracterWildcard(caracter)) {
                escapado.append('\\');
            }
            escapado.append(caracter);
        }
        return escapado.toString();
    }

    private static boolean esMetacaracterWildcard(char caracter) {
        return caracter == '*' || caracter == '?' || caracter == '\\';
    }

    private List<Producto> ordenarPorRelevancia(List<Producto> productos, String consulta) {
        // El comparador se invoca O(n log n) veces y puntuar() calcula Levenshtein:
        // precalcular el score una sola vez por producto evita que las exportaciones
        // (que traen decenas de miles de filas) se vuelvan lentas.
        Map<Producto, Integer> puntajes = new IdentityHashMap<>();
        for (Producto producto : productos) {
            puntajes.put(producto, ProductoTextoRelevanceScorer.puntuar(producto, consulta));
        }
        return productos.stream()
                .sorted(Comparator
                        .comparingInt((Producto producto) -> -puntajes.get(producto))
                        .thenComparing(Producto::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private void aplicarFiltrosIndexados(
            BooleanPredicateClausesStep<?> bool,
            SearchPredicateFactory f,
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
