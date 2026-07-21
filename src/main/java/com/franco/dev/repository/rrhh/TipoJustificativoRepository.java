package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.TipoJustificativo;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface TipoJustificativoRepository extends HelperRepository<TipoJustificativo, Long> {

    default Class<TipoJustificativo> getEntityClass() {
        return TipoJustificativo.class;
    }

    List<TipoJustificativo> findByActivoTrueOrderByNombreAsc();

    TipoJustificativo findFirstByNombreIgnoreCase(String nombre);
}
