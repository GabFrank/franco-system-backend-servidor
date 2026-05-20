package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PreGastoRepository extends HelperRepository<PreGasto, EmbebedPrimaryKey>,
        JpaSpecificationExecutor<PreGasto> {

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

    @Query(value = "SELECT max(pg.id) FROM financiero.pre_gasto pg WHERE pg.sucursal_id = CAST(:sucursalId AS bigint)", nativeQuery = true)
    Long findMaxId(@Param("sucursalId") Long sucursalId);
}
