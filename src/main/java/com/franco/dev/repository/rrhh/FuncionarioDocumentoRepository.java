package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.FuncionarioDocumento;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface FuncionarioDocumentoRepository extends HelperRepository<FuncionarioDocumento, Long> {

    default Class<FuncionarioDocumento> getEntityClass() {
        return FuncionarioDocumento.class;
    }

    List<FuncionarioDocumento> findByFuncionarioIdAndAnuladoFalseOrderByFechaSubidaDesc(Long funcionarioId);

    List<FuncionarioDocumento> findByAnuladoFalseAndVencimientoBetween(java.time.LocalDate desde, java.time.LocalDate hasta);
}
