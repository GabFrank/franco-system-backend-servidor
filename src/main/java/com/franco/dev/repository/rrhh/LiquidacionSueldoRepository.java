package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.HelperRepository;

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
}
