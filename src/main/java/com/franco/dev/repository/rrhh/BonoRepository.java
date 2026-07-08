package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface BonoRepository extends HelperRepository<Bono, Long> {

    default Class<Bono> getEntityClass() {
        return Bono.class;
    }

    List<Bono> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);
}
