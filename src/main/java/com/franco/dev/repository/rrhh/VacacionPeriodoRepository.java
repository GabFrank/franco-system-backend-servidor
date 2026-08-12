package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.VacacionPeriodo;
import com.franco.dev.domain.rrhh.enums.VacacionPeriodoEstado;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface VacacionPeriodoRepository extends HelperRepository<VacacionPeriodo, Long> {

    default Class<VacacionPeriodo> getEntityClass() {
        return VacacionPeriodo.class;
    }

    List<VacacionPeriodo> findByVacacionIdOrderByFechaDesdeAsc(Long vacacionId);

    List<VacacionPeriodo> findByEstadoOrderByFechaDesdeAsc(VacacionPeriodoEstado estado);
}
