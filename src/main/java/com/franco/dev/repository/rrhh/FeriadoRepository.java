package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Feriado;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeriadoRepository extends HelperRepository<Feriado, Long> {

    default Class<Feriado> getEntityClass() {
        return Feriado.class;
    }

    Optional<Feriado> findByFecha(LocalDate fecha);

    List<Feriado> findByFechaBetweenOrderByFechaAsc(LocalDate desde, LocalDate hasta);

    List<Feriado> findByActivoTrueOrderByFechaAsc();
}
