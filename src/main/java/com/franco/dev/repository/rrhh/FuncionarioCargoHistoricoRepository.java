package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.FuncionarioCargoHistorico;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface FuncionarioCargoHistoricoRepository extends HelperRepository<FuncionarioCargoHistorico, Long> {

    default Class<FuncionarioCargoHistorico> getEntityClass() {
        return FuncionarioCargoHistorico.class;
    }

    List<FuncionarioCargoHistorico> findByFuncionarioIdOrderByFechaDesdeDesc(Long funcionarioId);

    List<FuncionarioCargoHistorico> findByFuncionarioIdAndFechaHastaIsNull(Long funcionarioId);
}
