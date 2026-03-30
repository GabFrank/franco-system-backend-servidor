package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.repository.HelperRepository;

import java.util.Optional;

public interface LocalRepository extends HelperRepository<Local, Long> {

    default Class<Local> getEntityClass() {
        return Local.class;
    }

    Optional<Local> findFirstByOrderByIdAsc();
}
