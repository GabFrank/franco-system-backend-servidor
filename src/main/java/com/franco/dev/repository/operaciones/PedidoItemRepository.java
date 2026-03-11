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
    
    /**
     * Busca PedidoItems por pedidoId filtrando por id, descripción o código del producto
     * @param id ID del pedido
     * @param texto Texto para buscar en id, descripción o cualquier código del producto
     * @param page Paginación
     * @return Página de PedidoItems
     */
    @Query("SELECT DISTINCT pi FROM PedidoItem pi " +
           "WHERE pi.pedido.id = :pedidoId " +
           "AND (CAST(pi.producto.id AS string) LIKE :texto " +
           "     OR UPPER(pi.producto.descripcion) LIKE UPPER(:texto) " +
           "     OR EXISTS (SELECT 1 FROM Presentacion pres " +
           "                WHERE pres.producto = pi.producto " +
           "                AND EXISTS (SELECT 1 FROM Codigo c " +
           "                           WHERE c.presentacion = pres " +
           "                           AND UPPER(c.codigo) LIKE UPPER(:texto)))) " +
           "ORDER BY pi.id DESC")
    Page<PedidoItem> findByPedidoIdAndProductoFilterOrderByIdDesc(
        @Param("pedidoId") Long id, 
        @Param("texto") String texto, 
        Pageable page
    );
    // public Page<PedidoItem> findByProductoIdAndPedidoEstado(Long productoId, PedidoEstado estado, Pageable pageable);
    
    // ===== BASIC COUNTING METHODS =====
    public Integer countByPedidoId(Long id);
    
    /**
     * Obtiene la lista de IDs de productos que ya están en el pedido
     * @param pedidoId ID del pedido
     * @return Lista de IDs de productos
     */
    @Query("SELECT DISTINCT pi.producto.id FROM PedidoItem pi WHERE pi.pedido.id = :pedidoId")
    List<Long> findProductoIdsByPedidoId(@Param("pedidoId") Long pedidoId);

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

    /**
     * Busca PedidoItems por pedidoId filtrando solo aquellos con cantidad pendiente > 0
     * cantidadPendiente = cantidadSolicitada - sum(cantidadEnNota) de todos los NotaRecepcionItem asociados
     * @param id ID del pedido
     * @param page Paginación
     * @return Página de PedidoItems con cantidad pendiente > 0
     */
    @Query(value = "SELECT pi.* FROM operaciones.pedido_item pi " +
           "WHERE pi.pedido_id = :pedidoId " +
           "AND (pi.cantidad_solicitada - COALESCE((" +
           "  SELECT SUM(nri.cantidad_en_nota) " +
           "  FROM operaciones.nota_recepcion_item nri " +
           "  WHERE nri.pedido_item_id = pi.id" +
           "), 0.0)) > 0 " +
           "ORDER BY pi.id DESC",
           countQuery = "SELECT COUNT(*) FROM operaciones.pedido_item pi " +
           "WHERE pi.pedido_id = :pedidoId " +
           "AND (pi.cantidad_solicitada - COALESCE((" +
           "  SELECT SUM(nri.cantidad_en_nota) " +
           "  FROM operaciones.nota_recepcion_item nri " +
           "  WHERE nri.pedido_item_id = pi.id" +
           "), 0.0)) > 0",
           nativeQuery = true)
    Page<PedidoItem> findByPedidoIdConCantidadPendienteOrderByIdDesc(
        @Param("pedidoId") Long id, 
        Pageable page
    );

    /**
     * Busca PedidoItems por pedidoId con filtro de texto y cantidad pendiente > 0
     * @param id ID del pedido
     * @param texto Texto para buscar en id, descripción o código del producto
     * @param page Paginación
     * @return Página de PedidoItems con cantidad pendiente > 0 que coinciden con el texto
     */
    @Query(value = "SELECT DISTINCT pi.* FROM operaciones.pedido_item pi " +
           "JOIN empresarial.producto p ON pi.producto_id = p.id " +
           "LEFT JOIN empresarial.presentacion pres ON pres.producto_id = p.id " +
           "LEFT JOIN empresarial.codigo c ON c.presentacion_id = pres.id " +
           "WHERE pi.pedido_id = :pedidoId " +
           "AND (pi.cantidad_solicitada - COALESCE((" +
           "  SELECT SUM(nri.cantidad_en_nota) " +
           "  FROM operaciones.nota_recepcion_item nri " +
           "  WHERE nri.pedido_item_id = pi.id" +
           "), 0.0)) > 0 " +
           "AND (CAST(pi.producto_id AS VARCHAR) LIKE :texto " +
           "     OR UPPER(p.descripcion) LIKE UPPER(:texto) " +
           "     OR UPPER(c.codigo) LIKE UPPER(:texto)) " +
           "ORDER BY pi.id DESC",
           countQuery = "SELECT COUNT(DISTINCT pi.id) FROM operaciones.pedido_item pi " +
           "JOIN empresarial.producto p ON pi.producto_id = p.id " +
           "LEFT JOIN empresarial.presentacion pres ON pres.producto_id = p.id " +
           "LEFT JOIN empresarial.codigo c ON c.presentacion_id = pres.id " +
           "WHERE pi.pedido_id = :pedidoId " +
           "AND (pi.cantidad_solicitada - COALESCE((" +
           "  SELECT SUM(nri.cantidad_en_nota) " +
           "  FROM operaciones.nota_recepcion_item nri " +
           "  WHERE nri.pedido_item_id = pi.id" +
           "), 0.0)) > 0 " +
           "AND (CAST(pi.producto_id AS VARCHAR) LIKE :texto " +
           "     OR UPPER(p.descripcion) LIKE UPPER(:texto) " +
           "     OR UPPER(c.codigo) LIKE UPPER(:texto))",
           nativeQuery = true)
    Page<PedidoItem> findByPedidoIdConCantidadPendienteAndTextoOrderByIdDesc(
        @Param("pedidoId") Long id, 
        @Param("texto") String texto, 
        Pageable page
    );

}

