package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.Actualizacion;
import com.franco.dev.domain.configuracion.enums.TipoActualizacion;
import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ActualizacionRepository extends HelperRepository<Actualizacion, Long> {

    default Class<Actualizacion> getEntityClass() {
        return Actualizacion.class;
    }

    public Actualizacion findFirstByTipoOrderByIdDesc(TipoActualizacion tipo);

}