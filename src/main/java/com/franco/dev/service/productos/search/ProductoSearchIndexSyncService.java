package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Producto;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

/**
 * Mantiene el índice Lucene al día en cada alta, edición o baja de productos y códigos.
 * Complementa la indexación automática de Hibernate Search para garantizar consistencia inmediata.
 */
@Service
public class ProductoSearchIndexSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProductoSearchIndexSyncService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.search.producto.enabled:true}")
    private boolean productoSearchEnabled;

    public void sincronizarProducto(Long productoId) {
        if (!productoSearchEnabled || productoId == null) {
            return;
        }
        try {
            Producto producto = entityManager.find(Producto.class, productoId);
            if (producto == null) {
                return;
            }
            session().indexingPlan().addOrUpdate(producto);
        } catch (Exception e) {
            log.warn("Índice Lucene: no se pudo sincronizar producto id={}", productoId, e);
        }
    }

    public void eliminarProducto(Long productoId) {
        if (!productoSearchEnabled || productoId == null) {
            return;
        }
        try {
            eliminarCodigosDeProducto(productoId);
            session().indexingPlan().purge(Producto.class, productoId, null);
        } catch (Exception e) {
            log.warn("Índice Lucene: no se pudo eliminar producto id={}", productoId, e);
        }
    }

    private void eliminarCodigosDeProducto(Long productoId) {
        List<Long> codigoIds = entityManager.createQuery(
                        "select c.id from Codigo c where c.presentacion.producto.id = :productoId",
                        Long.class)
                .setParameter("productoId", productoId)
                .getResultList();
        for (Long codigoId : codigoIds) {
            eliminarCodigo(codigoId);
        }
    }

    public void sincronizarCodigo(Long codigoId) {
        if (!productoSearchEnabled || codigoId == null) {
            return;
        }
        try {
            Codigo codigo = entityManager.find(Codigo.class, codigoId);
            if (codigo == null) {
                return;
            }
            inicializarRelacionesCodigo(codigo);
            session().indexingPlan().addOrUpdate(codigo);
        } catch (Exception e) {
            log.warn("Índice Lucene: no se pudo sincronizar código id={}", codigoId, e);
        }
    }

    public void eliminarCodigo(Long codigoId) {
        if (!productoSearchEnabled || codigoId == null) {
            return;
        }
        try {
            session().indexingPlan().purge(Codigo.class, codigoId, null);
        } catch (Exception e) {
            log.warn("Índice Lucene: no se pudo eliminar código id={}", codigoId, e);
        }
    }

    private SearchSession session() {
        return Search.session(entityManager);
    }

    private void inicializarRelacionesCodigo(Codigo codigo) {
        if (codigo.getPresentacion() != null && codigo.getPresentacion().getProducto() != null) {
            codigo.getPresentacion().getProducto().getId();
        }
    }
}
