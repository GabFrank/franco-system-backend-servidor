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

    /**
     * Toma el documento con lock pesimista antes de resolver su obligacion de pago.
     *
     * <p>Sin esto, dos pedidos de pago concurrentes sobre el mismo documento (doble click,
     * reintento de red) leen los dos {@code solicitud_pago_id == null}, crean una solicitud
     * cada uno y terminan pagando dos veces el mismo sueldo. El indice unico no lo impide:
     * evita que dos documentos compartan una solicitud, no que un documento reciba dos.</p>
     */
    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from LiquidacionFinal e where e.id = :id")
    java.util.Optional<LiquidacionFinal> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}
