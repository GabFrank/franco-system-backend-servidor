package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface ValeRepository extends HelperRepository<Vale, Long> {

    default Class<Vale> getEntityClass() {
        return Vale.class;
    }

    List<Vale> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);

    List<Vale> findByEstadoOrderByFechaDesc(ValeEstado estado);

    List<Vale> findByFuncionarioIdAndEstado(Long funcionarioId, ValeEstado estado);
}
