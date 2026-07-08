package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Penalizacion;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDate;
import java.util.List;

public interface PenalizacionRepository extends HelperRepository<Penalizacion, Long> {

    default Class<Penalizacion> getEntityClass() {
        return Penalizacion.class;
    }

    List<Penalizacion> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);

    List<Penalizacion> findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(Long funcionarioId, LocalDate desde, LocalDate hasta);

    List<Penalizacion> findByJornadaIdAndSucursalId(Long jornadaId, Long sucursalId);

    List<Penalizacion> findByJornadaIdAndSucursalIdAndAutoGeneradaTrueAndAnuladaFalse(Long jornadaId, Long sucursalId);
}
