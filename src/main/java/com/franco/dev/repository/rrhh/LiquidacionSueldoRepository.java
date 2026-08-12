package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LiquidacionSueldoRepository extends HelperRepository<LiquidacionSueldo, Long> {

    default Class<LiquidacionSueldo> getEntityClass() {
        return LiquidacionSueldo.class;
    }

    List<LiquidacionSueldo> findByFuncionarioIdOrderByPeriodoDesc(Long funcionarioId);

    Optional<LiquidacionSueldo> findByFuncionarioIdAndPeriodo(Long funcionarioId, String periodo);

    List<LiquidacionSueldo> findByPeriodoOrderByIdAsc(String periodo);

    List<LiquidacionSueldo> findByEstadoOrderByPeriodoDesc(LiquidacionSueldoEstado estado);

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select l from LiquidacionSueldo l where " +
            "(:funcionarioId is null or l.funcionario.id = :funcionarioId) and " +
            "(:periodo is null or l.periodo = :periodo) and " +
            "(:estado is null or l.estado = :estado) " +
            "order by l.periodo desc, l.id desc")
    Page<LiquidacionSueldo> findPage(@Param("funcionarioId") Long funcionarioId,
                                     @Param("periodo") String periodo,
                                     @Param("estado") LiquidacionSueldoEstado estado,
                                     Pageable pageable);

    // Serie de nómina por período (los períodos son 'YYYY-MM', ordenan lexicográfico).
    // Solo liquidaciones APROBADA/PAGADA (lo efectivamente liquidado). Native SQL:
    // el enum se guarda como texto (@Enumerated(STRING)).
    @Query(value = "SELECT l.periodo AS periodo, COUNT(l.id) AS cantidad, " +
            "COALESCE(SUM(l.total_neto), 0) AS monto " +
            "FROM rrhh.liquidacion_sueldo l " +
            "WHERE l.periodo BETWEEN :periodoInicio AND :periodoFin " +
            "AND l.estado IN ('APROBADA', 'PAGADA') " +
            "GROUP BY l.periodo ORDER BY l.periodo",
            nativeQuery = true)
    List<Object[]> nominaSeriePorMesRaw(@Param("periodoInicio") String periodoInicio,
                                        @Param("periodoFin") String periodoFin);
}
