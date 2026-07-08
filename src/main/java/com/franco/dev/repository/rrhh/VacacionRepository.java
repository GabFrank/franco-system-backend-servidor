package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Vacacion;
import com.franco.dev.repository.HelperRepository;

import java.util.List;
import java.util.Optional;

public interface VacacionRepository extends HelperRepository<Vacacion, Long> {

    default Class<Vacacion> getEntityClass() {
        return Vacacion.class;
    }

    List<Vacacion> findByFuncionarioIdOrderByAnioServicioDesc(Long funcionarioId);

    Optional<Vacacion> findByFuncionarioIdAndAnioServicio(Long funcionarioId, Integer anioServicio);

    List<Vacacion> findByFuncionarioIdAndPrescritaFalse(Long funcionarioId);

    List<Vacacion> findByPrescritaFalse();
}
