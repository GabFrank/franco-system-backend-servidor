package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.JornadaNovedad;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDate;
import java.util.List;

public interface JornadaNovedadRepository extends HelperRepository<JornadaNovedad, Long> {

    default Class<JornadaNovedad> getEntityClass() {
        return JornadaNovedad.class;
    }

    List<JornadaNovedad> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);

    List<JornadaNovedad> findByFuncionarioIdAndFecha(Long funcionarioId, LocalDate fecha);

    List<JornadaNovedad> findByFuncionarioIdAndFechaBetweenOrderByFechaAsc(Long funcionarioId, LocalDate desde, LocalDate hasta);

    List<JornadaNovedad> findByJornadaIdAndSucursalId(Long jornadaId, Long sucursalId);
}
