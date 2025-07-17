package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoItemRepository extends HelperRepository<PedidoItem, Long> {
    default Class<PedidoItem> getEntityClass() {
        return PedidoItem.class;
    }

    // ===== BASIC CRUD METHODS =====
    public Page<PedidoItem> findByPedidoIdOrderByIdDesc(Long id, Pageable page);
    public List<PedidoItem> findByPedidoId(Long id);
    public List<PedidoItem> findByProductoId(Long id);
    public List<PedidoItem> findByIdIn(List<Long> idList);
    
    // ===== BASIC FILTERING METHODS =====
    public Page<PedidoItem> findByPedidoIdAndProductoDescripcionLikeOrderByIdDesc(Long id, String texto, Pageable page);
    public Page<PedidoItem> findByProductoIdAndPedidoEstado(Long productoId, PedidoEstado estado, Pageable pageable);
    
    // ===== BASIC COUNTING METHODS =====
    public Integer countByPedidoId(Long id);

    // ===== DISTRIBUTION METHODS (PedidoItemDistribucion) =====
    /**
     * Get total distributed quantity for a specific PedidoItem from PedidoItemDistribucion
     */
    @Query("select coalesce(sum(pis.cantidadAsignada), 0.0) " +
           "from PedidoItemDistribucion pis " +
           "where pis.pedidoItem.id = :pedidoItemId")
    Double getTotalDistributedQuantityByPedidoItemId(@Param("pedidoItemId") Long pedidoItemId);

}

