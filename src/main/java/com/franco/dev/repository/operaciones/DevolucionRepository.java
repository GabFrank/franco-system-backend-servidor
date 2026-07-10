package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.dto.DevolucionPorEstadoDto;
import com.franco.dev.domain.operaciones.dto.DevolucionSeriePuntoDto;
import com.franco.dev.domain.operaciones.dto.ResumenDevolucionesDto;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DevolucionRepository extends HelperRepository<Devolucion, Long> {
    
    default Class<Devolucion> getEntityClass() {
        return Devolucion.class;
    }

    // Nota: cada parametro nullable se envuelve con cast(:param as <tipo>) en el IS NULL.
    // Sin el cast, PostgreSQL no puede inferir el tipo del bind cuando llega null
    // ("no se pudo determinar el tipo de dato del parametro") y la query revienta.
    // Mismo idiom usado en FuncionarioRepository / EventoInutilizacionDERepository.
    @Query("SELECT d FROM Devolucion d " +
           "WHERE (cast(:proveedorId as long) IS NULL OR d.proveedor.id = :proveedorId) " +
           "AND (cast(:sucursalId as long) IS NULL OR d.sucursalOrigen.id = :sucursalId) " +
           "AND (cast(:estado as com.franco.dev.domain.operaciones.enums.DevolucionEstado) IS NULL OR d.estado = :estado) " +
           "AND (cast(:fechaInicio as timestamp) IS NULL OR d.fecha >= :fechaInicio) " +
           "AND (cast(:fechaFin as timestamp) IS NULL OR d.fecha <= :fechaFin) " +
           "ORDER BY d.fecha DESC")
    Page<Devolucion> findByFilters(
            @Param("proveedorId") Long proveedorId,
            @Param("sucursalId") Long sucursalId,
            @Param("estado") DevolucionEstado estado,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            Pageable pageable
    );

    @Query("SELECT d FROM Devolucion d WHERE d.proveedor.id = :proveedorId ORDER BY d.fecha DESC")
    List<Devolucion> findByProveedorId(@Param("proveedorId") Long proveedorId);

    @Query("SELECT d FROM Devolucion d WHERE d.estado = :estado")
    List<Devolucion> findByEstado(@Param("estado") DevolucionEstado estado);

    @Query("SELECT d FROM Devolucion d WHERE d.proveedor.id = :proveedorId " +
           "AND d.estado IN :estados ORDER BY d.fecha DESC")
    List<Devolucion> findByProveedorIdAndEstados(@Param("proveedorId") Long proveedorId,
                                                 @Param("estados") List<DevolucionEstado> estados);

    /**
     * Devoluciones de un proveedor en un estado dado. Ambos parametros no-null.
     */
    @Query("SELECT d FROM Devolucion d WHERE d.proveedor.id = :proveedorId " +
           "AND d.estado = :estado ORDER BY d.sucursalOrigen.id ASC, d.fecha ASC")
    List<Devolucion> findByProveedorIdAndEstado(@Param("proveedorId") Long proveedorId,
                                                @Param("estado") DevolucionEstado estado);

    /**
     * Devoluciones de un proveedor en un estado dado, con sucursal opcional (nullable).
     * Usa el idiom cast(:param as tipo) en el IS NULL para que PostgreSQL pueda
     * determinar el tipo del parametro cuando llega null.
     */
    @Query("SELECT d FROM Devolucion d " +
           "WHERE d.proveedor.id = :proveedorId " +
           "AND d.estado = :estado " +
           "AND (cast(:sucursalId as long) IS NULL OR d.sucursalOrigen.id = :sucursalId) " +
           "ORDER BY d.sucursalOrigen.id ASC, d.fecha ASC")
    List<Devolucion> findByProveedorIdEstadoAndSucursal(@Param("proveedorId") Long proveedorId,
                                                        @Param("estado") DevolucionEstado estado,
                                                        @Param("sucursalId") Long sucursalId);

    // ===================== Agregaciones para el dashboard =====================
    // Todas filtran por rango de fecha (obligatorio) y sucursal (nullable con el
    // idiom cast(:param as long)). Los conteos son a nivel Devolucion; el valor
    // (costoUnitario x cantidad) agrega a nivel item y se completa en el service.

    @Query("SELECT new com.franco.dev.domain.operaciones.dto.ResumenDevolucionesDto(" +
           "COUNT(d), " +
           "SUM(CASE WHEN d.tipo = com.franco.dev.domain.operaciones.enums.TipoDevolucion.CON_PROVEEDOR THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN d.tipo = com.franco.dev.domain.operaciones.enums.TipoDevolucion.SIN_PROVEEDOR THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN d.tipo = com.franco.dev.domain.operaciones.enums.TipoDevolucion.CON_PROVEEDOR " +
           "     AND d.estado IN (com.franco.dev.domain.operaciones.enums.DevolucionEstado.PENDIENTE, " +
           "                      com.franco.dev.domain.operaciones.enums.DevolucionEstado.SEPARADO) THEN 1 ELSE 0 END)) " +
           "FROM Devolucion d " +
           "WHERE d.fecha >= :fechaInicio AND d.fecha <= :fechaFin " +
           "AND (cast(:sucursalId as long) IS NULL OR d.sucursalOrigen.id = :sucursalId)")
    ResumenDevolucionesDto resumenConteos(@Param("fechaInicio") LocalDateTime fechaInicio,
                                          @Param("fechaFin") LocalDateTime fechaFin,
                                          @Param("sucursalId") Long sucursalId);

    @Query("SELECT new com.franco.dev.domain.operaciones.dto.DevolucionPorEstadoDto(" +
           "cast(d.estado as string), COUNT(d)) " +
           "FROM Devolucion d " +
           "WHERE d.fecha >= :fechaInicio AND d.fecha <= :fechaFin " +
           "AND (cast(:sucursalId as long) IS NULL OR d.sucursalOrigen.id = :sucursalId) " +
           "GROUP BY d.estado")
    List<DevolucionPorEstadoDto> conteoPorEstado(@Param("fechaInicio") LocalDateTime fechaInicio,
                                                 @Param("fechaFin") LocalDateTime fechaFin,
                                                 @Param("sucursalId") Long sucursalId);

    // Serie por dia. Native SQL: JPQL no tiene truncado de fecha portable. El
    // LEFT JOIN a items suma el valor sin perder devoluciones sin items.
    // Se usa CAST(... AS date), no ::date: el "::" de PostgreSQL choca con la
    // sintaxis :param de Hibernate y rompe la query.
    @Query(value = "SELECT to_char(CAST(d.fecha AS date), 'YYYY-MM-DD') AS fecha, " +
           "COUNT(DISTINCT d.id) AS cantidad, " +
           "COALESCE(SUM(di.costo_unitario * di.cantidad), 0) AS valor " +
           "FROM operaciones.devolucion d " +
           "LEFT JOIN operaciones.devolucion_item di ON di.devolucion_id = d.id " +
           "WHERE d.fecha BETWEEN :fechaInicio AND :fechaFin " +
           "AND (CAST(:sucursalId AS bigint) IS NULL OR d.sucursal_origen_id = :sucursalId) " +
           "GROUP BY CAST(d.fecha AS date) ORDER BY CAST(d.fecha AS date)",
           nativeQuery = true)
    List<Object[]> seriePorDiaRaw(@Param("fechaInicio") LocalDateTime fechaInicio,
                                  @Param("fechaFin") LocalDateTime fechaFin,
                                  @Param("sucursalId") Long sucursalId);
} 