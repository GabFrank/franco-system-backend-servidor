package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.FuncionarioSalarioHistorico;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface FuncionarioSalarioHistoricoRepository extends HelperRepository<FuncionarioSalarioHistorico, Long> {

    default Class<FuncionarioSalarioHistorico> getEntityClass() {
        return FuncionarioSalarioHistorico.class;
    }

    List<FuncionarioSalarioHistorico> findByFuncionarioIdOrderByFechaVigenciaDesc(Long funcionarioId);
}
