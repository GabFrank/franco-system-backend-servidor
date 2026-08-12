package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.DevolucionConfiguracion;
import com.franco.dev.repository.HelperRepository;

public interface DevolucionConfiguracionRepository
        extends HelperRepository<DevolucionConfiguracion, Long> {

    default Class<DevolucionConfiguracion> getEntityClass() {
        return DevolucionConfiguracion.class;
    }
}
