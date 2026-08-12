package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.VacacionVenta;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface VacacionVentaRepository extends HelperRepository<VacacionVenta, Long> {

    default Class<VacacionVenta> getEntityClass() {
        return VacacionVenta.class;
    }

    List<VacacionVenta> findByVacacionIdOrderByFechaDesc(Long vacacionId);
}
