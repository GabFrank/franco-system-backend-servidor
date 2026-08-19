package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.repository.HelperRepository;

import java.util.List;
import java.util.Optional;

public interface LiquidacionFinalRepository extends HelperRepository<LiquidacionFinal, Long> {

    default Class<LiquidacionFinal> getEntityClass() {
        return LiquidacionFinal.class;
    }

    List<LiquidacionFinal> findByFuncionarioIdOrderByCreadoEnDesc(Long funcionarioId);

    Optional<LiquidacionFinal> findFirstByFuncionarioIdAndEstadoOrderByCreadoEnDesc(Long funcionarioId, LiquidacionFinalEstado estado);

    List<LiquidacionFinal> findByEstadoOrderByCreadoEnDesc(LiquidacionFinalEstado estado);

    /** Documento dueno de una obligacion de pago (puente tesoreria, V199.5). */
    LiquidacionFinal findBySolicitudPagoId(Long solicitudPagoId);
}
