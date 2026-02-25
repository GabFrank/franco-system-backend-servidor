package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaRecepcionRepository extends HelperRepository<NotaRecepcion, Long> {
    default Class<NotaRecepcion> getEntityClass() {
        return NotaRecepcion.class;
    }

    public List<NotaRecepcion> findByPedidoId(Long id);

    public Page<NotaRecepcion> findByPedidoId(Long id, Pageable page);

    @Query(value =
            "SELECT nr FROM NotaRecepcion nr " +
                    "WHERE nr.pedido.id = :id " +
                    "AND (nr.numero IS NULL OR cast(nr.numero AS text) LIKE :text) order by nr.id desc"
    )
    Page<NotaRecepcion> findByPedidoIdAndNumero(
            Long id,
            String text,
            Pageable pageable
    );

    @Query(value = "select sum((pi.precio_unitario_recepcion_nota - pi.descuento_unitario_recepcion_nota) * (pi.cantidad_recepcion_nota * p.cantidad)) from operaciones.nota_recepcion nr " +
            "join operaciones.pedido_item pi on nr.id = pi.nota_recepcion_id " +
            "join productos.presentacion p on p.id = pi.presentacion_recepcion_nota_id " +
            "where (pi.cancelado is null or pi.cancelado = false) " +
            "and (pi.motivo_rechazo_recepcion_nota is null or pi.motivo_rechazo_recepcion_nota = '') " +
            "and nr.id = ?1", nativeQuery = true)
    public Double valor(Long id);

    /**
     * Calcula el valor total de una nota de recepción usando la nueva estructura
     * Suma: cantidadEnNota * precioUnitarioEnNota de todos los ítems de la nota
     * Excluye ítems rechazados y bonificaciones (esBonificacion = true)
     */
    @Query(value = "SELECT COALESCE(SUM(nri.cantidad_en_nota * nri.precio_unitario_en_nota), 0.0) " +
            "FROM operaciones.nota_recepcion_item nri " +
            "WHERE nri.nota_recepcion_id = :notaRecepcionId " +
            "AND (nri.es_bonificacion IS NULL OR nri.es_bonificacion = false) " +
            "AND (nri.estado != 'RECHAZADO' OR nri.estado IS NULL)", nativeQuery = true)
    public Double valorTotal(@Param("notaRecepcionId") Long notaRecepcionId);

    /**
     * Calcula el valor total descontando rechazos parciales (RecepcionMercaderiaItem.cantidadRechazada).
     * Por ítem: (cantidad_en_nota - total_rechazada) * precio_unitario_en_nota.
     * Excluye ítems con estado RECHAZADO y bonificaciones.
     */
    @Query(value = "SELECT COALESCE(SUM(" +
            "GREATEST(0, (nri.cantidad_en_nota - COALESCE((" +
            "  SELECT SUM(rmi.cantidad_rechazada) FROM operaciones.recepcion_mercaderia_item rmi " +
            "  WHERE rmi.nota_recepcion_item_id = nri.id" +
            "), 0)) * nri.precio_unitario_en_nota)), 0.0) " +
            "FROM operaciones.nota_recepcion_item nri " +
            "WHERE nri.nota_recepcion_id = :notaRecepcionId " +
            "AND (nri.es_bonificacion IS NULL OR nri.es_bonificacion = false) " +
            "AND (nri.estado != 'RECHAZADO' OR nri.estado IS NULL)", nativeQuery = true)
    Double valorTotalConRechazos(@Param("notaRecepcionId") Long notaRecepcionId);

    public Integer countByPedidoId(Long id);

    public Integer countByPedidoIdAndPagadoTrue(Long id);

    @Query(value = "SELECT CASE  " +
            "             WHEN COUNT(*) = 0 THEN FALSE " +
            "             WHEN BOOL_AND(pagado) THEN TRUE " +
            "             ELSE FALSE " +
            "           END " +
            "    FROM operaciones.nota_recepcion " +
            "    WHERE pedido_id = :pedidoId "
            , nativeQuery = true)
    public Boolean areAllNotasPagadasTrue(@Param("pedidoId") Long pedidoId);

//     public List<NotaRecepcion> findByPedidoProveedorIdAndNumeroAndPedidoEstadoNot(Long id, Integer numero, PedidoEstado estado);

//     @Query(value = "SELECT nr FROM NotaRecepcion nr " +
//             "WHERE nr.numero = :numero " +
//             "AND nr.pedido.estado != :estado " +
//             "ORDER BY nr.id DESC")
//     public List<NotaRecepcion> findByNumeroAndPedidoEstadoNot(@Param("numero") Integer numero, @Param("estado") PedidoEstado estado);

    // REMOVED - Methods related to NotaRecepcionAgrupada (deleted entity):
    // public List<NotaRecepcion> findByNotaRecepcionAgrupadaId(Long id);
    // public Page<NotaRecepcion> findByNotaRecepcionAgrupadaId(Long id, Pageable page);
    // public Long countByNotaRecepcionAgrupadaId(Long id);

    /**
     * Find NotaRecepcion available for reception with filtering criteria
     * UPDATED: Simplified query since NotaRecepcionAgrupada was removed in refactor
     * @param numero Optional nota number (can be null if proveedor is provided)
     * @param proveedorId Optional proveedor ID (can be null if numero is provided)
     * @param sucursalId Optional sucursal ID for filtering by PedidoItemDistribucion
     * @return List of available NotaRecepcion for reception
     */
    @Query("SELECT DISTINCT nr FROM NotaRecepcion nr " +
           "LEFT JOIN nr.pedido p " +
           "LEFT JOIN PedidoItem pi ON pi.pedido = p " +
           "LEFT JOIN PedidoItemDistribucion pis ON pis.pedidoItem = pi " +
           "WHERE " +
           "(:numero IS NULL OR nr.numero = :numero) " +
           "AND (:proveedorId IS NULL OR p.proveedor.id = :proveedorId) " +
           "AND (:sucursalId IS NULL OR pis.sucursalEntrega.id = :sucursalId) " +
           "ORDER BY nr.id DESC")
    List<NotaRecepcion> findNotasDisponiblesParaRecepcion(
            @Param("numero") Integer numero,
            @Param("proveedorId") Long proveedorId,
            @Param("sucursalId") Long sucursalId
    );

    /**
     * Find NotaRecepcion available for reception with filtering criteria and pagination
     * UPDATED: Simplified query since NotaRecepcionAgrupada was removed in refactor
     * @param numero Optional nota number (can be null if proveedor is provided)
     * @param proveedorId Optional proveedor ID (can be null if numero is provided)
     * @param sucursalId Optional sucursal ID for filtering by PedidoItemDistribucion
     * @param pageable Pagination parameters
     * @return Page of available NotaRecepcion for reception
     */
    @Query("SELECT DISTINCT nr FROM NotaRecepcion nr " +
           "LEFT JOIN nr.pedido p " +
           "LEFT JOIN PedidoItem pi ON pi.pedido = p " +
           "LEFT JOIN PedidoItemDistribucion pis ON pis.pedidoItem = pi " +
           "WHERE " +
           "(:numero IS NULL OR nr.numero = :numero) " +
           "AND (:proveedorId IS NULL OR p.proveedor.id = :proveedorId) " +
           "AND (:sucursalId IS NULL OR pis.sucursalEntrega.id = :sucursalId) " +
           "ORDER BY nr.id DESC")
    Page<NotaRecepcion> findNotasDisponiblesParaRecepcionPage(
            @Param("numero") Integer numero,
            @Param("proveedorId") Long proveedorId,
            @Param("sucursalId") Long sucursalId,
            Pageable pageable
    );

    /**
     * Find NotaRecepcion eligible for payment by numero and proveedor.
     * Estado must be RECEPCION_COMPLETA (recepción física finalizada); pagado must be null or false.
     */
    @Query("SELECT nr FROM NotaRecepcion nr JOIN nr.pedido p WHERE nr.numero = :numero " +
           "AND p.proveedor.id = :proveedorId AND nr.estado IN :estados " +
           "AND (nr.pagado IS NULL OR nr.pagado = false) ORDER BY nr.id DESC")
    List<NotaRecepcion> findDisponiblesParaPagoPorNumeroYProveedor(
            @Param("numero") Integer numero,
            @Param("proveedorId") Long proveedorId,
            @Param("estados") List<NotaRecepcionEstado> estados
    );

    /**
     * Find NotaRecepcion eligible for payment by proveedor (all available notes for that provider).
     * Estado must be RECEPCION_COMPLETA (recepción física finalizada); pagado null or false.
     * Caller must filter by isNotaIncludedInSolicitud.
     */
    @Query("SELECT nr FROM NotaRecepcion nr JOIN nr.pedido p WHERE p.proveedor.id = :proveedorId " +
           "AND nr.estado IN :estados AND (nr.pagado IS NULL OR nr.pagado = false) ORDER BY nr.id DESC")
    List<NotaRecepcion> findDisponiblesParaPagoPorProveedor(
            @Param("proveedorId") Long proveedorId,
            @Param("estados") List<NotaRecepcionEstado> estados
    );

    /**
     * Paginated: NotaRecepcion eligible for payment by proveedor, excluding those already linked to any SolicitudPago.
     * Optional filter by numero (filtroNumeroPattern e.g. "%123%" for LIKE; null = no filter).
     */
    @Query("SELECT nr FROM NotaRecepcion nr JOIN nr.pedido p WHERE p.proveedor.id = :proveedorId " +
           "AND nr.estado IN :estados AND (nr.pagado IS NULL OR nr.pagado = false) " +
           "AND NOT EXISTS (SELECT 1 FROM SolicitudPagoNotaRecepcion spnr WHERE spnr.notaRecepcion.id = nr.id) " +
           "AND (:filtroNumeroPattern IS NULL OR CAST(nr.numero AS string) LIKE :filtroNumeroPattern) " +
           "ORDER BY nr.id DESC")
    Page<NotaRecepcion> findDisponiblesParaPagoPorProveedorPage(
            @Param("proveedorId") Long proveedorId,
            @Param("estados") List<NotaRecepcionEstado> estados,
            @Param("filtroNumeroPattern") String filtroNumeroPattern,
            Pageable pageable
    );
}