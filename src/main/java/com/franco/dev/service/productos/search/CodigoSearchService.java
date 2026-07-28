package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.repository.productos.CodigoRepository;
import com.franco.dev.utilitarios.BarcodeSearchUtils;
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

    /**
     * Punto único de búsqueda "inteligente" por código de barras, compartido por lista de
     * productos, transferencias y gestión de compras.
     *
     * Encuentra el producto escribiendo el código completo, sin los ceros a la izquierda,
     * o solo un tramo interno / la terminación. Los resultados vienen ordenados por
     * calidad de coincidencia (exacto > prefijo > sufijo > infijo).
     */
    public List<Long> buscarProductoIdsPorCoincidencia(String texto, int maxResults) {
        if (texto == null) {
            return Collections.emptyList();
        }
        String base = texto.trim();
        if (base.length() < MIN_PREFIJO || base.contains(" ")) {
            return Collections.emptyList();
        }
        int limit = maxResults > 0 ? maxResults : 50;

        Set<Long> ids = new LinkedHashSet<>();
        for (String fragmento : fragmentosDeBusqueda(base)) {
            if (ids.size() >= limit) {
                break;
            }
            for (Long id : codigoRepository.findProductoIdsByCodigoCoincidencia(fragmento, limit)) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Variantes a probar, de más específica a más amplia: el texto tal cual, los candidatos
     * derivados del escaneo (pesable, GTIN GS1, token alfanumérico) y el texto sin los ceros
     * a la izquierda, para que "078470" y "78470" lleguen al mismo producto.
     */
    private Set<String> fragmentosDeBusqueda(String base) {
        Set<String> fragmentos = new LinkedHashSet<>();
        fragmentos.add(base.toUpperCase());

        for (String candidato : BarcodeSearchUtils.codigosParaBuscar(base)) {
            if (candidato.length() >= MIN_PREFIJO) {
                fragmentos.add(candidato.toUpperCase());
            }
        }

        String sinCeros = base.replaceFirst("^0+", "");
        if (sinCeros.length() >= MIN_PREFIJO) {
            fragmentos.add(sinCeros.toUpperCase());
        }
        return fragmentos;
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
