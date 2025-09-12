package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.ConstanciaDeRecepcion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConstanciaDeRecepcionRepository extends HelperRepository<ConstanciaDeRecepcion, Long> {
    
    default Class<ConstanciaDeRecepcion> getEntityClass() {
        return ConstanciaDeRecepcion.class;
    }
}

