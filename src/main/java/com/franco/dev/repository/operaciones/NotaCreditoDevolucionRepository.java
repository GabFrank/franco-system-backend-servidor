package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaCreditoDevolucion;
import com.franco.dev.repository.HelperRepository;

import java.util.List;
import java.util.Optional;

public interface NotaCreditoDevolucionRepository extends HelperRepository<NotaCreditoDevolucion, Long> {

    default Class<NotaCreditoDevolucion> getEntityClass() {
        return NotaCreditoDevolucion.class;
    }

    /**
     * La nota vigente del retiro: a lo sumo una no ANULADA (indice unico parcial
     * uq_nota_credito_retiro_vigente). Se pasa ESTADO_ANULADA como estado.
     */
    Optional<NotaCreditoDevolucion> findFirstByRetiroIdAndEstadoNotOrderByIdDesc(Long retiroId, String estado);

    /** Notas ANULADAS del retiro, la mas reciente primero. Se pasa ESTADO_ANULADA. */
    List<NotaCreditoDevolucion> findByRetiroIdAndEstadoOrderByIdDesc(Long retiroId, String estado);
}
