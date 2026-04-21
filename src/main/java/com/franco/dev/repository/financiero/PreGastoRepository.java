package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface PreGastoRepository extends HelperRepository<PreGasto, EmbebedPrimaryKey> {

    PreGasto findByIdAndSucursalId(Long id, Long sucursalId);

    default Class<PreGasto> getEntityClass() {
        return PreGasto.class;
    }

    List<PreGasto> findByEstadoOrderByIdDesc(EstadoPreGasto estado);

    @Query(value = "SELECT pg.* FROM financiero.pre_gasto pg " +
            "WHERE pg.estado = CAST(:estado AS financiero.estado_pre_gasto) " +
            "AND pg.sucursal_id = CAST(:sucursalId AS bigint) " +
            "ORDER BY pg.id DESC", nativeQuery = true)
    List<PreGasto> buscarPorEstadoYSucursal(@Param("estado") String estado, @Param("sucursalId") Long sucursalId);

    @Query(value = "SELECT pg.* FROM financiero.pre_gasto pg " +
            "WHERE pg.funcionario_id = CAST(:funcionarioId AS bigint) " +
            "ORDER BY pg.id DESC", nativeQuery = true)
    List<PreGasto> buscarPorFuncionario(@Param("funcionarioId") Long funcionarioId);

    @Query(value = "SELECT pg.* FROM financiero.pre_gasto pg " +
            "WHERE pg.estado = CAST(:estado AS financiero.estado_pre_gasto) " +
            "ORDER BY pg.id DESC", nativeQuery = true)
    List<PreGasto> buscarPorEstado(@Param("estado") String estado);

    @Query(value = "SELECT pg.* FROM financiero.pre_gasto pg " +
            "WHERE (CAST(pg.id AS text) LIKE CONCAT('%', :texto, '%') " +
            "OR UPPER(pg.descripcion) LIKE CONCAT('%', UPPER(:texto), '%')) " +
            "AND (CAST(:sucursalId AS bigint) IS NULL OR pg.sucursal_id = CAST(:sucursalId AS bigint)) " +
            "ORDER BY pg.id DESC", nativeQuery = true)
    List<PreGasto> buscarPorTexto(@Param("texto") String texto, @Param("sucursalId") Long sucursalId);

    @Query(value = "SELECT pg.* FROM financiero.pre_gasto pg " +
            "WHERE (CAST(:id AS bigint) IS NULL OR pg.id = CAST(:id AS bigint)) " +
            "AND (CAST(:estado AS text) IS NULL OR cast(pg.estado as text) = CAST(:estado AS text)) " +
            "AND cast(pg.estado as text) IN (:estados) " +
            "AND (CAST(:inicio AS text) IS NULL OR pg.creado_en >= CAST(CAST(:inicio AS text) AS timestamp)) " +
            "AND (CAST(:fin AS text) IS NULL OR pg.creado_en <= CAST(CAST(:fin AS text) AS timestamp)) " +
            "ORDER BY pg.id DESC",
            countQuery = "SELECT count(*) FROM financiero.pre_gasto pg " +
            "WHERE (CAST(:id AS bigint) IS NULL OR pg.id = CAST(:id AS bigint)) " +
            "AND (CAST(:estado AS text) IS NULL OR cast(pg.estado as text) = CAST(:estado AS text)) " +
            "AND cast(pg.estado as text) IN (:estados) " +
            "AND (CAST(:inicio AS text) IS NULL OR pg.creado_en >= CAST(CAST(:inicio AS text) AS timestamp)) " +
            "AND (CAST(:fin AS text) IS NULL OR pg.creado_en <= CAST(CAST(:fin AS text) AS timestamp))",
            nativeQuery = true)
    Page<PreGasto> filterPreGastos(@Param("id") Long id,
                                   @Param("estado") String estado,
                                   @Param("estados") List<String> estados,
                                   @Param("inicio") String inicio,
                                   @Param("fin") String fin,
                                   Pageable pageable);

    @Query(value = "SELECT pg.* FROM financiero.pre_gasto pg " +
            "WHERE (CAST(:id AS bigint) IS NULL OR pg.id = CAST(:id AS bigint)) " +
            "AND (CAST(:estado AS text) IS NULL OR cast(pg.estado as text) = CAST(:estado AS text)) " +
            "AND (CAST(:inicio AS text) IS NULL OR pg.creado_en >= CAST(CAST(:inicio AS text) AS timestamp)) " +
            "AND (CAST(:fin AS text) IS NULL OR pg.creado_en <= CAST(CAST(:fin AS text) AS timestamp)) " +
            "ORDER BY pg.id DESC",
            countQuery = "SELECT count(*) FROM financiero.pre_gasto pg " +
                    "WHERE (CAST(:id AS bigint) IS NULL OR pg.id = CAST(:id AS bigint)) " +
                    "AND (CAST(:estado AS text) IS NULL OR cast(pg.estado as text) = CAST(:estado AS text)) " +
                    "AND (CAST(:inicio AS text) IS NULL OR pg.creado_en >= CAST(CAST(:inicio AS text) AS timestamp)) " +
                    "AND (CAST(:fin AS text) IS NULL OR pg.creado_en <= CAST(CAST(:fin AS text) AS timestamp))",
            nativeQuery = true)
    Page<PreGasto> filterPreGastosSinEstados(@Param("id") Long id,
                                             @Param("estado") String estado,
                                             @Param("inicio") String inicio,
                                             @Param("fin") String fin,
                                             Pageable pageable);

    @Query(value = "SELECT max(pg.id) FROM financiero.pre_gasto pg WHERE pg.sucursal_id = CAST(:sucursalId AS bigint)", nativeQuery = true)
    Long findMaxId(@Param("sucursalId") Long sucursalId);
}
