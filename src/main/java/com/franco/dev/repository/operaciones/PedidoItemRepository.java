package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
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
    // public Page<PedidoItem> findByProductoIdAndPedidoEstado(Long productoId, PedidoEstado estado, Pageable pageable);
    
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

    // ===== NOTA RECEPCION METHODS =====
    /**
     * Find PedidoItemDistribucion by PedidoItem ID
     */
    @Query("select pid from PedidoItemDistribucion pid where pid.pedidoItem.id = :pedidoItemId")
    List<com.franco.dev.domain.operaciones.PedidoItemDistribucion> findByPedidoItemId(@Param("pedidoItemId") Long pedidoItemId);

    /**
     * Calcula la cantidad pendiente de conciliar para un PedidoItem
     * cantidadPendiente = cantidadSolicitada - sum(cantidadEnNota) de todos los NotaRecepcionItem asociados
     */
    @Query(value = "SELECT pi.cantidad_solicitada - COALESCE((" +
           "  SELECT SUM(nri.cantidad_en_nota) " +
           "  FROM operaciones.nota_recepcion_item nri " +
           "  WHERE nri.pedido_item_id = pi.id" +
           "), 0.0) " +
           "FROM operaciones.pedido_item pi " +
           "WHERE pi.id = :pedidoItemId", nativeQuery = true)
    Double getCantidadPendienteByPedidoItemId(@Param("pedidoItemId") Long pedidoItemId);

}

