package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Justificativo;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDate;
import java.util.List;

public interface JustificativoRepository extends HelperRepository<Justificativo, Long> {

    default Class<Justificativo> getEntityClass() {
        return Justificativo.class;
    }

    List<Justificativo> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);

    List<Justificativo> findByFuncionarioIdAndFecha(Long funcionarioId, LocalDate fecha);

    List<Justificativo> findByFuncionarioIdAndFechaBetweenOrderByFechaAsc(Long funcionarioId, LocalDate desde, LocalDate hasta);

    List<Justificativo> findByJornadaIdAndSucursalId(Long jornadaId, Long sucursalId);
}
