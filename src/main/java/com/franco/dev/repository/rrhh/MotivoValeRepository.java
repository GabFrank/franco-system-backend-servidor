package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.MotivoVale;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface MotivoValeRepository extends HelperRepository<MotivoVale, Long> {

    default Class<MotivoVale> getEntityClass() {
        return MotivoVale.class;
    }

    List<MotivoVale> findByActivoTrueOrderByNombreAsc();
}
