package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionItemProgramado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemProgramadoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface LiquidacionItemProgramadoRepository extends HelperRepository<LiquidacionItemProgramado, Long> {

    default Class<LiquidacionItemProgramado> getEntityClass() {
        return LiquidacionItemProgramado.class;
    }

    List<LiquidacionItemProgramado> findByFuncionarioIdAndPeriodoAndEstadoOrderByIdAsc(
            Long funcionarioId, String periodo, LiquidacionItemProgramadoEstado estado);

    List<LiquidacionItemProgramado> findByFuncionarioIdAndEstadoOrderByPeriodoAscIdAsc(
            Long funcionarioId, LiquidacionItemProgramadoEstado estado);

    List<LiquidacionItemProgramado> findByFuncionarioIdOrderByPeriodoDescIdDesc(Long funcionarioId);

    /** Toma el programado con lock pesimista al aplicarlo, revertirlo o anularlo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LiquidacionItemProgramado p where p.id = :id")
    Optional<LiquidacionItemProgramado> lockById(@Param("id") Long id);
}
