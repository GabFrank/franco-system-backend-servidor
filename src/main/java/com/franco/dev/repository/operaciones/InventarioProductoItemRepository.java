package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.enums.InventarioProductoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.List;

public interface InventarioProductoItemRepository extends HelperRepository<InventarioProductoItem, Long> {

    default Class<InventarioProductoItem> getEntityClass() {
        return InventarioProductoItem.class;
    }

    List<InventarioProductoItem> findByInventarioProductoIdOrderByIdDesc(Long id, Pageable pageable);

    List<InventarioProductoItem> findByInventarioProductoId(Long id);

    List<InventarioProductoItem> findByInventarioProductoInventarioIdAndPresentacionProductoId(Long inventarioId, Long productoId);

    @Query("SELECT i FROM InventarioProductoItem i " +
            "JOIN i.inventarioProducto ip " +
            "JOIN ip.inventario inv " +
            "WHERE inv.id = :inventarioId " +
            "ORDER BY CASE " +
            "WHEN :filtro = 'cantidadExacta' AND i.verificado = true AND (i.revisado = false OR i.revisado IS NULL) THEN 0 " +
            "WHEN :filtro = 'modificado' AND i.revisado = true AND (i.verificado = false OR i.verificado IS NULL) THEN 0 " +
            "ELSE 1 END, i.id DESC")
    Page<InventarioProductoItem> findItemsParaRevisar(
            @Param("inventarioId") Long inventarioId,
            @Param("filtro") String filtro,
            Pageable pageable);

    @Query("SELECT i FROM InventarioProductoItem i " +
            "JOIN i.presentacion pre " +
            "JOIN pre.producto pro " +
            "JOIN i.inventarioProducto ip " +
            "JOIN ip.inventario inv " +
            "JOIN inv.sucursal s " +
            "LEFT JOIN i.zona z " +
            "LEFT JOIN z.sector sec " +
            "JOIN i.usuario u " +
            "WHERE i.creadoEn BETWEEN :startDate AND :endDate " +
            "AND (COALESCE(:sucursalIdList, NULL) IS NULL OR s.id IN :sucursalIdList) " +
            "AND (COALESCE(:sectorIdList, NULL) IS NULL OR sec.id IN :sectorIdList) " +
            "AND (COALESCE(:zonaIdList, NULL) IS NULL OR z.id IN :zonaIdList) " +
            "AND (COALESCE(:usuarioIdList, NULL) IS NULL OR u.id IN :usuarioIdList) " +
            "AND (COALESCE(:productoIdList, NULL) IS NULL OR pro.id IN :productoIdList)")
    Page<InventarioProductoItem> findAllWithFilters(
            @Param("sucursalIdList") @Nullable List<Long> sucursalIdList,
            @Param("sectorIdList") @Nullable List<Long> sectorIdList,
            @Param("zonaIdList") @Nullable List<Long> zonaIdList,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("usuarioIdList") @Nullable List<Long> usuarioIdList,
            @Param("productoIdList") @Nullable List<Long> productoIdList,
            Pageable pageable);

    @Query(value = "SELECT ipi.* FROM operaciones.inventario_producto_item ipi " +
            "LEFT JOIN operaciones.inventario_producto ip ON ip.id = ipi.inventario_producto_id " +
            "LEFT JOIN operaciones.inventario i ON i.id = ip.inventario_id " +
            "LEFT JOIN productos.presentacion p ON p.id = ipi.presentacion_id " +
            "LEFT JOIN productos.producto p2 ON p2.id = p.producto_id " +
            "WHERE i.id = ?1 AND p2.id = ?2", nativeQuery = true)
    List<InventarioProductoItem> findByInventarioIdAndProductoId(Long inventarioId, Long productoId);

    @Query("SELECT i FROM InventarioProductoItem i " +
            "JOIN i.inventarioProducto ip " +
            "JOIN ip.inventario inv " +
            "JOIN i.presentacion pre " +
            "JOIN pre.producto pro " +
            "JOIN inv.sucursal s " +
            "LEFT JOIN i.zona z " +
            "LEFT JOIN z.sector sec " +
            "JOIN i.usuario u " +
            "WHERE (i.vencimiento < CURRENT_TIMESTAMP OR i.estado = :estadoVencido) " +
            "AND (COALESCE(:sucursalIdList, NULL) IS NULL OR s.id IN :sucursalIdList) " +
            "AND (COALESCE(:sectorIdList, NULL) IS NULL OR sec.id IN :sectorIdList) " +
            "AND (COALESCE(:zonaIdList, NULL) IS NULL OR z.id IN :zonaIdList) " +
            "AND (COALESCE(:usuarioIdList, NULL) IS NULL OR u.id IN :usuarioIdList) " +
            "AND (COALESCE(:productoIdList, NULL) IS NULL OR pro.id IN :productoIdList) " +
            "ORDER BY i.vencimiento DESC")
    Page<InventarioProductoItem> findProductosVencidos(
            @Param("sucursalIdList") @Nullable List<Long> sucursalIdList,
            @Param("sectorIdList") @Nullable List<Long> sectorIdList,
            @Param("zonaIdList") @Nullable List<Long> zonaIdList,
            @Param("usuarioIdList") @Nullable List<Long> usuarioIdList,
            @Param("productoIdList") @Nullable List<Long> productoIdList,
            @Param("estadoVencido") InventarioProductoEstado estadoVencido,
            Pageable pageable);

    default Page<InventarioProductoItem> findProductosVencidos(
            List<Long> sucursalIdList,
            List<Long> sectorIdList,
            List<Long> zonaIdList,
            List<Long> usuarioIdList,
            List<Long> productoIdList,
            Pageable pageable) {
        return findProductosVencidos(sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList,
                InventarioProductoEstado.VENCIDO, pageable);
    }
    @Query("SELECT i FROM InventarioProductoItem i " +
            "JOIN i.inventarioProducto ip " +
            "JOIN ip.inventario inv " +
            "JOIN i.presentacion pre " +
            "JOIN pre.producto pro " +
            "JOIN inv.sucursal s " +
            "LEFT JOIN i.zona z " +
            "LEFT JOIN z.sector sec " +
            "JOIN i.usuario u " +
            "WHERE i.vencimiento BETWEEN :startDate AND :endDate " +
            "AND (COALESCE(:sucursalIdList, NULL) IS NULL OR s.id IN :sucursalIdList) " +
            "AND (COALESCE(:sectorIdList, NULL) IS NULL OR sec.id IN :sectorIdList) " +
            "AND (COALESCE(:zonaIdList, NULL) IS NULL OR z.id IN :zonaIdList) " +
            "AND (COALESCE(:usuarioIdList, NULL) IS NULL OR u.id IN :usuarioIdList) " +
            "AND (COALESCE(:productoIdList, NULL) IS NULL OR pro.id IN :productoIdList) " +
            "ORDER BY i.vencimiento DESC")
    Page<InventarioProductoItem> findProductosVencidosConFecha(
            @Param("sucursalIdList") @Nullable List<Long> sucursalIdList,
            @Param("sectorIdList") @Nullable List<Long> sectorIdList,
            @Param("zonaIdList") @Nullable List<Long> zonaIdList,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("usuarioIdList") @Nullable List<Long> usuarioIdList,
            @Param("productoIdList") @Nullable List<Long> productoIdList,
            Pageable pageable);
    @Query("SELECT i FROM InventarioProductoItem i " +
            "JOIN i.inventarioProducto ip " +
            "JOIN ip.inventario inv " +
            "JOIN i.presentacion pre " +
            "JOIN pre.producto pro " +
            "JOIN inv.sucursal s " +
            "LEFT JOIN i.zona z " +
            "LEFT JOIN z.sector sec " +
            "JOIN i.usuario u " +
            "WHERE (COALESCE(:sucursalIdList, NULL) IS NULL OR s.id IN :sucursalIdList) " +
            "AND (COALESCE(:sectorIdList, NULL) IS NULL OR sec.id IN :sectorIdList) " +
            "AND (COALESCE(:zonaIdList, NULL) IS NULL OR z.id IN :zonaIdList) " +
            "AND (COALESCE(:usuarioIdList, NULL) IS NULL OR u.id IN :usuarioIdList) " +
            "AND (COALESCE(:productoIdList, NULL) IS NULL OR pro.id IN :productoIdList) " +
            "AND i.vencimiento BETWEEN CURRENT_TIMESTAMP AND :fechaProximoVencimiento " +
            "AND i.estado != :estadoVencido " +
            "ORDER BY i.vencimiento DESC")
    Page<InventarioProductoItem> findProductosProximosAVencer(
            @Param("sucursalIdList") @Nullable List<Long> sucursalIdList,
            @Param("sectorIdList") @Nullable List<Long> sectorIdList,
            @Param("zonaIdList") @Nullable List<Long> zonaIdList,
            @Param("usuarioIdList") @Nullable List<Long> usuarioIdList,
            @Param("productoIdList") @Nullable List<Long> productoIdList,
            @Param("fechaProximoVencimiento") LocalDateTime fechaProximoVencimiento,
            @Param("estadoVencido") InventarioProductoEstado estadoVencido,
            Pageable pageable);

    default Page<InventarioProductoItem> findProductosProximosAVencer(
            List<Long> sucursalIdList,
            List<Long> sectorIdList,
            List<Long> zonaIdList,
            List<Long> usuarioIdList,
            List<Long> productoIdList,
            LocalDateTime fechaProximoVencimiento,
            Pageable pageable) {
        return findProductosProximosAVencer(sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList,
                fechaProximoVencimiento, InventarioProductoEstado.VENCIDO, pageable);
    }

    @Query("SELECT COUNT(i) FROM InventarioProductoItem i " +
            "JOIN i.inventarioProducto ip " +
            "JOIN ip.inventario inv " +
            "JOIN i.presentacion pre " +
            "JOIN pre.producto pro " +
            "JOIN inv.sucursal s " +
            "LEFT JOIN i.zona z " +
            "LEFT JOIN z.sector sec " +
            "JOIN i.usuario u " +
            "WHERE (i.vencimiento < CURRENT_TIMESTAMP OR i.estado = :estadoVencido) " +
            "AND (COALESCE(:sucursalIdList, NULL) IS NULL OR s.id IN :sucursalIdList) " +
            "AND (COALESCE(:sectorIdList, NULL) IS NULL OR sec.id IN :sectorIdList) " +
            "AND (COALESCE(:zonaIdList, NULL) IS NULL OR z.id IN :zonaIdList) " +
            "AND (COALESCE(:usuarioIdList, NULL) IS NULL OR u.id IN :usuarioIdList) " +
            "AND (COALESCE(:productoIdList, NULL) IS NULL OR pro.id IN :productoIdList)")
    Long countProductosVencidos(
            @Param("sucursalIdList") @Nullable List<Long> sucursalIdList,
            @Param("sectorIdList") @Nullable List<Long> sectorIdList,
            @Param("zonaIdList") @Nullable List<Long> zonaIdList,
            @Param("usuarioIdList") @Nullable List<Long> usuarioIdList,
            @Param("productoIdList") @Nullable List<Long> productoIdList,
            @Param("estadoVencido") InventarioProductoEstado estadoVencido);

    default Long countProductosVencidos(
            List<Long> sucursalIdList,
            List<Long> sectorIdList,
            List<Long> zonaIdList,
            List<Long> usuarioIdList,
            List<Long> productoIdList) {
        return countProductosVencidos(sucursalIdList, sectorIdList, zonaIdList, usuarioIdList, productoIdList,
                InventarioProductoEstado.VENCIDO);
    }
}