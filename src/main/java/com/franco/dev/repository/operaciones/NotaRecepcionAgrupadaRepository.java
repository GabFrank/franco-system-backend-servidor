package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionAgrupada;
import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.operaciones.enums.PedidoRecepcionProductoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaRecepcionAgrupadaRepository extends HelperRepository<NotaRecepcionAgrupada, Long> {
    default Class<NotaRecepcionAgrupada> getEntityClass() {
        return NotaRecepcionAgrupada.class;
    }

    public Page<NotaRecepcionAgrupada> findByUsuarioIdOrderByIdDesc(Long id, Pageable page);

    public Page<NotaRecepcionAgrupada> findByProveedorId(Long id, Pageable page);

    /**
     * Find available grupos for solicitud pago with business logic filtering
     * Only returns grupos that can receive new notas for payment request.
     * 
     * Database-level filtering applied:
     * - Same proveedor.id
     * - Estado = 'EN_RECEPCION' (only active grupos)
     * - Ordered by creadoEn DESC (newest first)
     * 
     * Note: Related entities (proveedor, usuario, sucursal) are loaded lazily
     * as JOIN FETCH causes issues with pagination count queries in Spring Data JPA.
     * Payment status filtering is applied at service level since the 
     * SolicitudPago relationship is resolved through service lookup.
     * Removed cantNotas > 0 condition to allow empty grupos to receive notas.
     */
    @Query("SELECT g FROM NotaRecepcionAgrupada g " +
           "WHERE g.proveedor.id = :proveedorId " +
           "AND g.estado = 'EN_RECEPCION' " +
           "ORDER BY g.creadoEn DESC")
    Page<NotaRecepcionAgrupada> findGruposDisponiblesParaSolicitudPago(
        @Param("proveedorId") Long proveedorId, 
        Pageable pageable
    );

    /**
     * Alternative method with eager loading for specific use cases
     * Use this only when you need all related entities loaded and don't need pagination
     */
    @Query("SELECT g FROM NotaRecepcionAgrupada g " +
           "LEFT JOIN FETCH g.proveedor p " +
           "LEFT JOIN FETCH p.persona " +
           "LEFT JOIN FETCH g.usuario u " +
           "LEFT JOIN FETCH u.persona " +
           "LEFT JOIN FETCH g.sucursal s " +
           "WHERE g.proveedor.id = :proveedorId " +
           "AND g.estado = 'EN_RECEPCION' " +
           "ORDER BY g.creadoEn DESC")
    List<NotaRecepcionAgrupada> findGruposDisponiblesParaSolicitudPagoWithEagerLoading(
        @Param("proveedorId") Long proveedorId
    );

//    @Query(
//            value = "SELECT new com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto(" +
//                    "    prod, " +
//                    "    SUM(pis.cantidadPorUnidad), " +
//                    "    SUM(pis.cantidadPorUnidadRecibida)" +
//                    ") " +
//                    "FROM PedidoItem pi " +
//                    "JOIN pi.producto prod " +
//                    "JOIN PedidoItemSucursal pis ON pis.pedidoItem = pi " +
//                    "JOIN pi.pedido p " +
//                    "JOIN NotaRecepcion nr ON nr.pedido = p " +
//                    "JOIN nr.notaRecepcionAgrupada nra " +
//                    "WHERE nra.id = :notaRecepcionAgrupadaId " +
//                    "  AND pis.sucursalEntrega = nra.sucursal " +
//                    "GROUP BY prod " +
//                    "HAVING (:estado IS NULL OR " +
//                    "        (CASE " +
//                    "           WHEN SUM(pis.cantidadPorUnidadRecibida) IS NULL THEN 'PENDIENTE' " +
//                    "           WHEN SUM(pis.cantidadPorUnidadRecibida) >= SUM(pis.cantidadPorUnidad) THEN 'RECIBIDO' " +
//                    "           ELSE 'RECIBIDO_PARCIALMENTE' " +
//                    "         END) = :estado)"
//    )
//    public Page<PedidoRecepcionProductoDto> findRecepcionProductoByRecepcionByNotaAgrupada(
//            @Param("notaRecepcionAgrupadaId") Long notaRecepcionAgrupadaId,
//            @Param("estado") String estado,
//            Pageable pageable);

    @Query("SELECT new com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto(" +
            "    prod, " +
            "    SUM(pis.cantidadPorUnidad), " +
            "    SUM(pis.cantidadPorUnidadRecibida) " +
            ") " +
            "FROM NotaRecepcionAgrupada nra " +
            "JOIN NotaRecepcion nr WITH nr.notaRecepcionAgrupada = nra " +
            "JOIN PedidoItem pi WITH pi.notaRecepcion = nr " +
            "JOIN PedidoItemSucursal pis WITH pis.pedidoItem = pi AND (nra.sucursal IS NULL OR pis.sucursalEntrega = nra.sucursal) " +
            "JOIN pi.presentacionRecepcionNota pre " +
            "JOIN pre.producto prod " +
            "WHERE nra.id = :notaRecepcionAgrupadaId " +
            "GROUP BY prod.id, prod.descripcion " +
            "HAVING (:estado IS NULL OR  " +
            "       (CASE  " +
            "          WHEN SUM(pis.cantidadPorUnidadRecibida) IS NULL THEN 'PENDIENTE' " +
            "          WHEN SUM(pis.cantidadPorUnidadRecibida) >= SUM(pis.cantidadPorUnidad) THEN 'RECIBIDO' " +
            "          ELSE 'RECIBIDO_PARCIALMENTE' " +
            "        END) = :estado) ")
    Page<PedidoRecepcionProductoDto> findRecepcionProductoByRecepcionByNotaAgrupada(
            @Param("notaRecepcionAgrupadaId") Long notaRecepcionAgrupadaId,
            @Param("estado") String estado,
            Pageable pageable);




    @Query("SELECT new com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto(" +
            "    prod, " +
            "    SUM(pis.cantidadPorUnidad), " +
            "    SUM(pis.cantidadPorUnidadRecibida)" +
            ") " +
            "FROM PedidoItem pi " +
            "LEFT JOIN pi.producto prod " +
            "LEFT JOIN PedidoItemSucursal pis ON pis.pedidoItem = pi " +
            "LEFT JOIN pi.pedido p " +
            "LEFT JOIN NotaRecepcion nr ON nr.pedido = p " +
            "LEFT JOIN nr.notaRecepcionAgrupada nra " +
            "WHERE nra.id = :notaRecepcionAgrupadaId " +
            "  AND prod.id = :productoId AND (nra.sucursal IS NULL OR pis.sucursalEntrega = nra.sucursal) " +
            "GROUP BY prod " +
            "HAVING (:estado IS NULL OR " +
            "        (CASE " +
            "           WHEN SUM(pis.cantidadPorUnidadRecibida) IS NULL THEN 'PENDIENTE' " +
            "           WHEN SUM(pis.cantidadPorUnidadRecibida) >= SUM(pis.cantidadPorUnidad) THEN 'RECIBIDO' " +
            "           ELSE 'RECIBIDO_PARCIALMENTE' " +
            "         END) = :estado)")
    public PedidoRecepcionProductoDto findRecepcionProductoByRecepcionByNotaAgrupadaAndProducto(
            @Param("notaRecepcionAgrupadaId") Long notaRecepcionAgrupadaId,
            @Param("productoId") Long productoId,
            @Param("estado") String estado);

    /**
     * Find all NotaRecepcionAgrupada for a specific pedido
     * Now using direct pedido relationship for better performance
     */
    @Query("SELECT nra FROM NotaRecepcionAgrupada nra " +
           "WHERE nra.pedido.id = :pedidoId")
    List<NotaRecepcionAgrupada> findByPedidoId(@Param("pedidoId") Long pedidoId);

    /**
     * Find all NotaRecepcionAgrupada for a specific pedido with pagination
     * Used for solicitud-pago step to show grupos created for this specific pedido
     * Shows grupos regardless of SolicitudPago status - ordered by creation date (newest first)
     * Now using direct pedido relationship for better performance
     */
    @Query("SELECT nra FROM NotaRecepcionAgrupada nra " +
           "WHERE nra.pedido.id = :pedidoId " +
           "ORDER BY nra.creadoEn DESC")
    Page<NotaRecepcionAgrupada> findGruposByPedidoIdPaginated(@Param("pedidoId") Long pedidoId, Pageable pageable);

    /**
     * Alternative method with eager loading for specific use cases
     * Use this only when you need all related entities loaded and don't need pagination
     */

}
