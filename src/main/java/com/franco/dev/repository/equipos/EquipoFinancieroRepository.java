package com.franco.dev.repository.equipos;

import com.franco.dev.domain.equipos.EquipoFinanciero;
import com.franco.dev.repository.HelperRepository;

import java.util.Optional;

public interface EquipoFinancieroRepository extends HelperRepository<EquipoFinanciero, Long> {

    default Class<EquipoFinanciero> getEntityClass() {
        return EquipoFinanciero.class;
    }

    Optional<EquipoFinanciero> findByEquipoId(Long equipoId);
}
