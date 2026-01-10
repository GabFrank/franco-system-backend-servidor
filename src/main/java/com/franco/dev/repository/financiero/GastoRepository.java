package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.operaciones.MovimientoStock;
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

        public Gasto findByIdAndSucursalId(Long id, Long sucId);

        @Query("SELECT new com.franco.dev.domain.financiero.GastoPorCategoria(" +
                        "CASE " +
                        "   WHEN LOWER(g.observacion) LIKE '%almuerzo%' OR LOWER(g.observacion) LIKE '%cena%' OR LOWER(g.observacion) LIKE '%desayuno%' OR LOWER(g.observacion) LIKE '%merienda%' OR LOWER(g.observacion) LIKE '%super%' THEN 'Alimentación' "
                        +
                        "   ELSE 'Gastos Operativos' " +
                        "END, " +
                        "SUM(g.retiroGs), COUNT(g)) " +
                        "FROM Gasto g " +
                        "WHERE g.creadoEn BETWEEN :inicio AND :fin " +
                        "AND (:sucId IS NULL OR g.sucursalId = :sucId) " +
                        "AND g.activo = true " +
                        "GROUP BY (" +
                        "   CASE " +
                        "       WHEN LOWER(g.observacion) LIKE '%almuerzo%' OR LOWER(g.observacion) LIKE '%cena%' OR LOWER(g.observacion) LIKE '%desayuno%' OR LOWER(g.observacion) LIKE '%merienda%' OR LOWER(g.observacion) LIKE '%super%' THEN 'Alimentación' "
                        +
                        "       ELSE 'Gastos Operativos' " +
                        "   END" +
                        ") " +
                        "ORDER BY SUM(g.retiroGs) DESC")
        List<com.franco.dev.domain.financiero.GastoPorCategoria> gastosPorCategoria(
                        @Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin, @Param("sucId") Long sucId);

        @Query("SELECT new com.franco.dev.domain.financiero.GastoPorMes(CAST(extract(month from g.creadoEn) as integer), SUM(g.retiroGs), COUNT(g)) "
                        +
                        "FROM Gasto g " +
                        "WHERE g.creadoEn BETWEEN :inicio AND :fin " +
                        "AND (:sucId IS NULL OR g.sucursalId = :sucId) " +
                        "AND g.activo = true " +
                        "GROUP BY extract(month from g.creadoEn) " +
                        "ORDER BY extract(month from g.creadoEn)")
        List<com.franco.dev.domain.financiero.GastoPorMes> gastosPorMes(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin, @Param("sucId") Long sucId);

}