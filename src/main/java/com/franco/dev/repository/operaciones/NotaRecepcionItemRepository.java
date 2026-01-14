package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.graphql.operaciones.dto.ResumenItemsNotaDTO;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaRecepcionItemRepository extends HelperRepository<NotaRecepcionItem, Long> {
    default Class<NotaRecepcionItem> getEntityClass() {
        return NotaRecepcionItem.class;
    }

    @Query("SELECT nri FROM NotaRecepcionItem nri WHERE nri.notaRecepcion.id = :id ORDER BY nri.id")
    List<NotaRecepcionItem> findByNotaRecepcionId(@Param("id") Long id);
//
//    @Query("select p from Pedido p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Pedido> findByProveedor(String texto);
//
//    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
//    //public List<Producto> findbyAll(String texto);

    @Query( value = "select * from operaciones.pedido_item pi2 " +
            "left join operaciones.nota_recepcion_item nri on nri.pedido_item_id = pi2.id " +
            "where pi2.pedido_id = ?1 and nri.pedido_item_id isnull", nativeQuery = true)
    public List<NotaRecepcionItem> findByPedidoIdAndPedidoItemIsNull(Long id);

    public List<NotaRecepcionItem> findByPedidoItemId(Long pedidoItemId);

    /**
     * Obtiene ítems de nota de recepción filtrados por sucursales de entrega
     * Solo devuelve ítems que tienen distribución en las sucursales especificadas
     */
    @Query(value = "SELECT DISTINCT nri.* " +
           "FROM operaciones.nota_recepcion_item nri " +
           "INNER JOIN operaciones.nota_recepcion_item_distribucion nrid ON nrid.nota_recepcion_item_id = nri.id " +
           "WHERE nri.nota_recepcion_id = :notaRecepcionId " +
           "AND nrid.sucursal_entrega_id IN (:sucursalesIds) " +
           "ORDER BY nri.id", nativeQuery = true)
    Page<NotaRecepcionItem> findByNotaRecepcionIdAndSucursalesIds(@Param("notaRecepcionId") Long notaRecepcionId, 
                                                                   @Param("sucursalesIds") List<Long> sucursalesIds, 
                                                                   Pageable pageable);

    /**
     * Obtiene ítems de nota de recepción filtrados por sucursales de entrega y texto
     * Solo devuelve ítems que tienen distribución en las sucursales especificadas
     * Busca en todos los códigos del producto, no solo el principal
     */
    @Query(value = "SELECT DISTINCT nri.* " +
           "FROM operaciones.nota_recepcion_item nri " +
           "INNER JOIN operaciones.nota_recepcion_item_distribucion nrid ON nrid.nota_recepcion_item_id = nri.id " +
           "INNER JOIN productos.producto p ON p.id = nri.producto_id " +
           "LEFT JOIN productos.presentacion pres ON pres.producto_id = p.id " +
           "LEFT JOIN productos.codigo c ON c.presentacion_id = pres.id " +
           "WHERE nri.nota_recepcion_id = :notaRecepcionId " +
           "AND nrid.sucursal_entrega_id IN (:sucursalesIds) " +
           "AND (:filtroTexto IS NULL OR " +
           "LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :filtroTexto, '%')) OR " +
           "LOWER(c.codigo) LIKE LOWER(CONCAT('%', :filtroTexto, '%'))) " +
           "ORDER BY nri.id", nativeQuery = true)
    Page<NotaRecepcionItem> findByNotaRecepcionIdAndSucursalesIds(@Param("notaRecepcionId") Long notaRecepcionId, 
                                                                   @Param("sucursalesIds") List<Long> sucursalesIds,
                                                                   @Param("filtroTexto") String filtroTexto,
                                                                   Pageable pageable);

    /**
     * Busca ítems por nota de recepción y sucursales, excluyendo los que ya tienen recepción
     * Regla: Solo mostrar ítems que NO tienen RecepcionMercaderiaItem para las sucursales seleccionadas
     * Incluye filtro de texto por código de barras y nombre del producto
     * Busca en todos los códigos del producto, no solo el principal
     */
    @Query("SELECT nri FROM NotaRecepcionItem nri " +
           "WHERE nri.notaRecepcion.id = :notaRecepcionId " +
           "AND EXISTS (SELECT 1 FROM NotaRecepcionItemDistribucion nrid " +
           "           WHERE nrid.notaRecepcionItem = nri " +
           "           AND nrid.sucursalEntrega.id IN :sucursalesIds) " +
           "AND NOT EXISTS (SELECT 1 FROM RecepcionMercaderiaItem rmi " +
           "               WHERE rmi.notaRecepcionItem = nri " +
           "               AND rmi.sucursalEntrega.id IN :sucursalesIds) " +
           "AND (:filtroTexto IS NULL OR " +
           "LOWER(nri.producto.descripcion) LIKE LOWER(CONCAT('%', :filtroTexto, '%')) OR " +
           "EXISTS (SELECT 1 FROM Presentacion pres " +
           "        WHERE pres.producto = nri.producto " +
           "        AND EXISTS (SELECT 1 FROM Codigo c " +
           "                   WHERE c.presentacion = pres " +
           "                   AND LOWER(c.codigo) LIKE LOWER(CONCAT('%', :filtroTexto, '%')))))")
    Page<NotaRecepcionItem> findByNotaRecepcionIdAndSucursalesIdsAndNoVerificados(
            @Param("notaRecepcionId") Long notaRecepcionId,
            @Param("sucursalesIds") List<Long> sucursalesIds,
            @Param("filtroTexto") String filtroTexto,
            Pageable pageable);

    /**
     * Busca ítems por nota de recepción y sucursales, solo los que ya tienen recepción
     * Regla: Solo mostrar ítems que SÍ tienen RecepcionMercaderiaItem para las sucursales seleccionadas
     * Incluye filtro de texto por código de barras y nombre del producto
     * Busca en todos los códigos del producto, no solo el principal
     */
    @Query("SELECT nri FROM NotaRecepcionItem nri " +
           "WHERE nri.notaRecepcion.id = :notaRecepcionId " +
           "AND EXISTS (SELECT 1 FROM NotaRecepcionItemDistribucion nrid " +
           "           WHERE nrid.notaRecepcionItem = nri " +
           "           AND nrid.sucursalEntrega.id IN :sucursalesIds) " +
           "AND EXISTS (SELECT 1 FROM RecepcionMercaderiaItem rmi " +
           "           WHERE rmi.notaRecepcionItem = nri " +
           "           AND rmi.sucursalEntrega.id IN :sucursalesIds) " +
           "AND (:filtroTexto IS NULL OR " +
           "LOWER(nri.producto.descripcion) LIKE LOWER(CONCAT('%', :filtroTexto, '%')) OR " +
           "EXISTS (SELECT 1 FROM Presentacion pres " +
           "        WHERE pres.producto = nri.producto " +
           "        AND EXISTS (SELECT 1 FROM Codigo c " +
           "                   WHERE c.presentacion = pres " +
           "                   AND LOWER(c.codigo) LIKE LOWER(CONCAT('%', :filtroTexto, '%')))))")
    Page<NotaRecepcionItem> findByNotaRecepcionIdAndSucursalesIdsAndVerificados(
            @Param("notaRecepcionId") Long notaRecepcionId,
            @Param("sucursalesIds") List<Long> sucursalesIds,
            @Param("filtroTexto") String filtroTexto,
            Pageable pageable);

    /**
     * Busca ítems por nota de recepción y sucursales, solo los que tienen rechazos
     * Regla: Solo mostrar ítems que SÍ tienen RecepcionMercaderiaItem con cantidadRechazada > 0 para las sucursales seleccionadas
     * Incluye filtro de texto por código de barras y nombre del producto
     * Busca en todos los códigos del producto, no solo el principal
     */
    @Query("SELECT nri FROM NotaRecepcionItem nri " +
           "WHERE nri.notaRecepcion.id = :notaRecepcionId " +
           "AND EXISTS (SELECT 1 FROM NotaRecepcionItemDistribucion nrid " +
           "           WHERE nrid.notaRecepcionItem = nri " +
           "           AND nrid.sucursalEntrega.id IN :sucursalesIds) " +
           "AND EXISTS (SELECT 1 FROM RecepcionMercaderiaItem rmi " +
           "           WHERE rmi.notaRecepcionItem = nri " +
           "           AND rmi.sucursalEntrega.id IN :sucursalesIds " +
           "           AND rmi.cantidadRechazada > 0) " +
           "AND (:filtroTexto IS NULL OR " +
           "LOWER(nri.producto.descripcion) LIKE LOWER(CONCAT('%', :filtroTexto, '%')) OR " +
           "EXISTS (SELECT 1 FROM Presentacion pres " +
           "        WHERE pres.producto = nri.producto " +
           "        AND EXISTS (SELECT 1 FROM Codigo c " +
           "                   WHERE c.presentacion = pres " +
           "                   AND LOWER(c.codigo) LIKE LOWER(CONCAT('%', :filtroTexto, '%')))))")
    Page<NotaRecepcionItem> findByNotaRecepcionIdAndSucursalesIdsAndRechazados(
            @Param("notaRecepcionId") Long notaRecepcionId,
            @Param("sucursalesIds") List<Long> sucursalesIds,
            @Param("filtroTexto") String filtroTexto,
            Pageable pageable);

    /**
     * Calcula la cantidad pendiente de recibir físicamente para un NotaRecepcionItem
     * cantidadPendiente = cantidadEnNota - sum(cantidadRecibida) de todos los RecepcionMercaderiaItem asociados
     */
    @Query(value = "SELECT nri.cantidad_en_nota - COALESCE((" +
           "  SELECT SUM(rmi.cantidad_recibida) " +
           "  FROM operaciones.recepcion_mercaderia_item rmi " +
           "  WHERE rmi.nota_recepcion_item_id = nri.id" +
           "), 0.0) " +
           "FROM operaciones.nota_recepcion_item nri " +
           "WHERE nri.id = :notaRecepcionItemId", nativeQuery = true)
    Double getCantidadPendienteByNotaRecepcionItemId(Long notaRecepcionItemId);

    /**
     * Obtiene items de una nota de recepción de forma paginada
     * Incluye información completa del producto, presentación y moneda
     */
    @Query(value = "SELECT nri FROM NotaRecepcionItem nri " +
           "LEFT JOIN FETCH nri.producto p " +
           "LEFT JOIN FETCH nri.presentacionEnNota pres " +
           "LEFT JOIN FETCH nri.notaRecepcion nr " +
           "LEFT JOIN FETCH nr.moneda m " +
           "WHERE nri.notaRecepcion.id = :notaId " +
           "ORDER BY p.descripcion ASC",
           countQuery = "SELECT COUNT(nri) FROM NotaRecepcionItem nri " +
           "WHERE nri.notaRecepcion.id = :notaId")
    Page<NotaRecepcionItem> findItemsByNotaIdPaginados(
            @Param("notaId") Long notaId, 
            Pageable pageable);

    /**
     * Obtiene resumen de items por estado para múltiples notas
     */
    @Query("SELECT new com.franco.dev.graphql.operaciones.dto.ResumenItemsNotaDTO(" +
           "nri.notaRecepcion.id, " +
           "COUNT(nri), " +
           "SUM(nri.cantidadEnNota), " +
           "CAST(nri.estado AS string)) " +
           "FROM NotaRecepcionItem nri " +
           "WHERE nri.notaRecepcion.id IN :notaIds " +
           "GROUP BY nri.notaRecepcion.id, nri.estado")
    List<ResumenItemsNotaDTO> getResumenItemsPorNota(@Param("notaIds") List<Long> notaIds);

    /**
     * Obtiene resumen general de items para múltiples notas
     */
    @Query("SELECT " +
           "COUNT(nri) as totalItems, " +
           "SUM(nri.cantidadEnNota) as totalCantidad " +
           "FROM NotaRecepcionItem nri " +
           "WHERE nri.notaRecepcion.id IN :notaIds")
    Object[] getResumenGeneralItems(@Param("notaIds") List<Long> notaIds);

    /**
     * Obtiene las últimas compras de un producto desde NotaRecepcionItem
     * Ordenado por fecha de creación descendente
     */
    @Query(value = "SELECT nri FROM NotaRecepcionItem nri " +
           "WHERE nri.producto.id = :productoId " +
           "ORDER BY nri.creadoEn DESC",
           countQuery = "SELECT COUNT(nri) FROM NotaRecepcionItem nri " +
           "WHERE nri.producto.id = :productoId")
    Page<NotaRecepcionItem> findUltimasComprasByProductoId(@Param("productoId") Long productoId, Pageable pageable);
}