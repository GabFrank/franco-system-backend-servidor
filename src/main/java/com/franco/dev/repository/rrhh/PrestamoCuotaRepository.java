package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.PrestamoCuota;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDate;
import java.util.List;

public interface PrestamoCuotaRepository extends HelperRepository<PrestamoCuota, Long> {

    default Class<PrestamoCuota> getEntityClass() {
        return PrestamoCuota.class;
    }

    List<PrestamoCuota> findByPrestamoIdOrderByNumeroAsc(Long prestamoId);

    List<PrestamoCuota> findByEstadoAndFechaVencimientoBefore(PrestamoCuotaEstado estado, LocalDate fecha);
}
