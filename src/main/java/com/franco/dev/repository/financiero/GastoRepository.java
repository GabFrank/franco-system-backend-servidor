package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.repository.query.Param;

public interface GastoRepository extends HelperRepository<Gasto, EmbebedPrimaryKey> {

        default Class<Gasto> getEntityClass() {
                return Gasto.class;
        }

        @Query(value = "SELECT max(g.id) FROM financiero.gasto g WHERE g.sucursal_id = CAST(:sucursalId AS bigint)", nativeQuery = true)
        Long findMaxId(@Param("sucursalId") Long sucursalId);

        public List<Gasto> findBySucursalIdAndCreadoEnBetween(Long id, LocalDateTime inicio, LocalDateTime fin);

        public List<Gasto> findByCajaIdAndSucursalId(Long id, Long sucId);

        @Query("select r from Gasto r " +
                        "left join r.caja ca " +
                        "left join r.responsable res " +
                        "where " +
                        "(r.id = :id or :id is null) and " +
                        "(ca.id = :cajaId or :cajaId is null) and " +
                        "(r.sucursalId = :sucId or :sucId is null) and " +
                        "(res.id = :responsableId or :responsableId is null) and " +
                        "(r.observacion like :descripcion or :descripcion is null) " +
                        "order by r.id desc")
        List<Gasto> findByAll(@Param("id") Long id, @Param("cajaId") Long cajaId, @Param("sucId") Long sucId,
                        @Param("responsableId") Long responsableId, @Param("descripcion") String descripcion,
                        Pageable pageable);

        @Query("select r from Gasto r " +
                        "left join r.caja ca " +
                        "left join r.responsable res " +
                        "where " +
                        "(r.id = :id or :id is null) and " +
                        "(ca.id = :cajaId or :cajaId is null) and " +
                        "(r.sucursalId = :sucId or :sucId is null) and " +
                        "(res.id = :responsableId or :responsableId is null) and " +
                        "(r.observacion like :descripcion or :descripcion is null) " +
                        "order by r.id desc")
        Page<Gasto> findByAllPage(@Param("id") Long id, @Param("cajaId") Long cajaId, @Param("sucId") Long sucId,
                        @Param("responsableId") Long responsableId, @Param("descripcion") String descripcion,
                        Pageable pageable);

        @Query(value = "SELECT " +
                        "COALESCE(tg.descripcion, 'Sin Categoría'), " +
                        "SUM(g.retiro_gs - COALESCE(g.vuelto_gs, 0)), COUNT(g.id) " +
                        "FROM financiero.gasto g " +
                        "LEFT JOIN financiero.tipo_gasto tg ON g.tipo_gasto_id = tg.id " +
                        "WHERE g.creado_en BETWEEN :inicio AND :fin " +
                        "AND g.sucursal_id = :sucId " +
                        "AND g.activo = true " +
                        "AND (g.cancelado IS NOT TRUE) " +
                        "GROUP BY tg.descripcion " +
                        "ORDER BY SUM(g.retiro_gs) DESC", nativeQuery = true)
        List<Object[]> gastosPorCategoria(
                        @Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin, @Param("sucId") Long sucId);

        @Query(value = "SELECT " +
                        "COALESCE(tg.descripcion, 'Sin Categoría'), " +
                        "SUM(g.retiro_gs - COALESCE(g.vuelto_gs, 0)), COUNT(g.id) " +
                        "FROM financiero.gasto g " +
                        "LEFT JOIN financiero.tipo_gasto tg ON g.tipo_gasto_id = tg.id " +
                        "WHERE g.creado_en BETWEEN :inicio AND :fin " +
                        "AND g.activo = true " +
                        "AND (g.cancelado IS NOT TRUE) " +
                        "GROUP BY tg.descripcion " +
                        "ORDER BY SUM(g.retiro_gs) DESC", nativeQuery = true)
        List<Object[]> gastosPorCategoriaSinSucursal(
                        @Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin);

        @Query(value = "SELECT CAST(extract(month from g.creado_en) as integer), SUM(g.retiro_gs - g.vuelto_gs), COUNT(g.id) "
                        +
                        "FROM financiero.gasto g " +
                        "WHERE g.creado_en BETWEEN :inicio AND :fin " +
                        "AND g.sucursal_id = :sucId " +
                        "AND g.activo = true " +
                        "AND (g.cancelado IS NOT TRUE) " +
                        "GROUP BY extract(month from g.creado_en) " +
                        "ORDER BY extract(month from g.creado_en)", nativeQuery = true)
        List<Object[]> gastosPorMes(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin, @Param("sucId") Long sucId);

        @Query(value = "SELECT CAST(extract(month from g.creado_en) as integer), SUM(g.retiro_gs - g.vuelto_gs), COUNT(g.id) "
                        +
                        "FROM financiero.gasto g " +
                        "WHERE g.creado_en BETWEEN :inicio AND :fin " +
                        "AND g.activo = true " +
                        "AND (g.cancelado IS NOT TRUE) " +
                        "GROUP BY extract(month from g.creado_en) " +
                        "ORDER BY extract(month from g.creado_en)", nativeQuery = true)
        List<Object[]> gastosPorMesSinSucursal(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin);

        public Gasto findByIdAndSucursalId(Long id, Long sucId);

        Gasto findFirstByPreGastoIdAndPreGastoSucursalIdOrderByCreadoEnDescIdDesc(Long preGastoId,
                        Long preGastoSucursalId);
}