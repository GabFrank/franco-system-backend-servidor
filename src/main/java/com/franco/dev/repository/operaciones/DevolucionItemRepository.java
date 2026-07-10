package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.dto.TopMotivoDevolucionDto;
import com.franco.dev.domain.operaciones.dto.TopProductoDevueltoDto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DevolucionItemRepository extends HelperRepository<DevolucionItem, Long> {
    
    default Class<DevolucionItem> getEntityClass() {
        return DevolucionItem.class;
    }

    @Query("SELECT di FROM DevolucionItem di WHERE di.devolucion.id = :devolucionId")
    List<DevolucionItem> findByDevolucionId(@Param("devolucionId") Long devolucionId);

    @Query("SELECT di FROM DevolucionItem di WHERE di.producto.id = :productoId")
    List<DevolucionItem> findByProductoId(@Param("productoId") Long productoId);

    @Query("SELECT di FROM DevolucionItem di WHERE di.recepcionMercaderiaItem.id = :recepcionItemId")
    List<DevolucionItem> findByRecepcionMercaderiaItemId(@Param("recepcionItemId") Long recepcionItemId);

    // ===================== Agregaciones para el dashboard =====================

    // Valor = costoUnitario x cantidad. Se calcula en dos queries escalares en vez
    // de un CASE dentro de SUM: Hibernate 5 no acepta aritmetica en THEN. Sin items,
    // SUM devuelve null y el service lo trata como 0.
    @Query("SELECT SUM(di.costoUnitario * di.cantidad) FROM DevolucionItem di JOIN di.devolucion d " +
           "WHERE d.fecha >= :fechaInicio AND d.fecha <= :fechaFin " +
           "AND (cast(:sucursalId as long) IS NULL OR d.sucursalOrigen.id = :sucursalId)")
    Double valorTotal(@Param("fechaInicio") LocalDateTime fechaInicio,
                      @Param("fechaFin") LocalDateTime fechaFin,
                      @Param("sucursalId") Long sucursalId);

    @Query("SELECT SUM(di.costoUnitario * di.cantidad) FROM DevolucionItem di JOIN di.devolucion d " +
           "WHERE d.tipo = com.franco.dev.domain.operaciones.enums.TipoDevolucion.SIN_PROVEEDOR " +
           "AND d.fecha >= :fechaInicio AND d.fecha <= :fechaFin " +
           "AND (cast(:sucursalId as long) IS NULL OR d.sucursalOrigen.id = :sucursalId)")
    Double valorMerma(@Param("fechaInicio") LocalDateTime fechaInicio,
                      @Param("fechaFin") LocalDateTime fechaFin,
                      @Param("sucursalId") Long sucursalId);

    @Query("SELECT new com.franco.dev.domain.operaciones.dto.TopProductoDevueltoDto(" +
           "p.id, p.descripcion, SUM(di.cantidad), COALESCE(SUM(di.costoUnitario * di.cantidad), 0)) " +
           "FROM DevolucionItem di JOIN di.devolucion d JOIN di.producto p " +
           "WHERE d.fecha >= :fechaInicio AND d.fecha <= :fechaFin " +
           "AND (cast(:sucursalId as long) IS NULL OR d.sucursalOrigen.id = :sucursalId) " +
           "GROUP BY p.id, p.descripcion ORDER BY SUM(di.cantidad) DESC")
    List<TopProductoDevueltoDto> topProductos(@Param("fechaInicio") LocalDateTime fechaInicio,
                                              @Param("fechaFin") LocalDateTime fechaFin,
                                              @Param("sucursalId") Long sucursalId,
                                              Pageable pageable);

    @Query("SELECT new com.franco.dev.domain.operaciones.dto.TopMotivoDevolucionDto(" +
           "m.id, m.descripcion, COUNT(di), SUM(di.cantidad)) " +
           "FROM DevolucionItem di JOIN di.devolucion d JOIN di.motivoAveria m " +
           "WHERE d.fecha >= :fechaInicio AND d.fecha <= :fechaFin " +
           "AND (cast(:sucursalId as long) IS NULL OR d.sucursalOrigen.id = :sucursalId) " +
           "GROUP BY m.id, m.descripcion ORDER BY COUNT(di) DESC")
    List<TopMotivoDevolucionDto> topMotivos(@Param("fechaInicio") LocalDateTime fechaInicio,
                                            @Param("fechaFin") LocalDateTime fechaFin,
                                            @Param("sucursalId") Long sucursalId,
                                            Pageable pageable);
} 