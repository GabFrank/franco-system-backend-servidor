package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Producto;
import org.hibernate.search.mapper.orm.Search;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 * Detecta si los índices Lucene de productos y códigos están listos.
 * Usado para reindexación automática al arrancar, sin intervención manual.
 *
 * El estado se resuelve contando documentos indexados, NO mirando el directorio.
 * Con `hibernate.search.schema_management.strategy=create-or-update` Hibernate Search
 * crea el índice vacío -- con su archivo `segments_N` -- durante el bootstrap, o sea
 * antes del ApplicationReadyEvent que dispara la reindexacion. Un chequeo por archivos
 * no distingue ese indice recien creado de uno poblado, asi que la reindexacion
 * automatica nunca se dispara y el buscador queda mudo sin un solo error en el log.
 */
@Service
public class ProductoSearchIndexEstadoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoSearchIndexEstadoService.class);

    private final EntityManagerFactory entityManagerFactory;

    public ProductoSearchIndexEstadoService(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    /**
     * @deprecated Usar {@link #requiereReindexacionAutomatica()}.
     */
    @Deprecated
    public boolean indiceVacioONoExiste() {
        return requiereReindexacionAutomatica();
    }

    /**
     * Verdadero si el indice de productos o el de codigos esta vacio.
     */
    public boolean requiereReindexacionAutomatica() {
        return !indiceProductoPresente() || !indiceCodigoPresente();
    }

    /**
     * Verdadero cuando productos ya estan indexados pero codigos aun no
     * (por ejemplo, tras agregar el indice de codigos en una version nueva).
     */
    public boolean requiereReindexacionSoloCodigos() {
        return indiceProductoPresente() && !indiceCodigoPresente();
    }

    public boolean indiceProductoPresente() {
        return tieneDocumentos(Producto.class);
    }

    public boolean indiceCodigoPresente() {
        return tieneDocumentos(Codigo.class);
    }

    /**
     * Ante cualquier error al consultar el indice devuelve false, o sea "hay que reindexar".
     * Reindexar de mas cuesta unos segundos al arrancar; no reindexar deja al buscador
     * devolviendo cero resultados en silencio, que es mucho peor.
     */
    private boolean tieneDocumentos(Class<?> entidad) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            long documentos = Search.session(entityManager)
                    .search(entidad)
                    .where(f -> f.matchAll())
                    .fetchTotalHitCount();
            log.debug("Indice Lucene de {}: {} documentos", entidad.getSimpleName(), documentos);
            return documentos > 0;
        } catch (RuntimeException e) {
            log.warn("No se pudo consultar el indice Lucene de {} ({}). Se asume vacio y se reindexa.",
                    entidad.getSimpleName(), e.getMessage());
            return false;
        } finally {
            entityManager.close();
        }
    }
}
