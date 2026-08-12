package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaCreditoDevolucion;
import com.franco.dev.repository.HelperRepository;

import java.util.Optional;

public interface NotaCreditoDevolucionRepository extends HelperRepository<NotaCreditoDevolucion, Long> {

    default Class<NotaCreditoDevolucion> getEntityClass() {
        return NotaCreditoDevolucion.class;
    }

    /** Una nota de credito por retiro (1:1). */
    Optional<NotaCreditoDevolucion> findByRetiroId(Long retiroId);
}
