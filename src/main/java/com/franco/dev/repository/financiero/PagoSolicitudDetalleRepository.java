package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PagoSolicitudDetalleRepository extends JpaRepository<PagoSolicitudDetalle, Long> {
    List<PagoSolicitudDetalle> findBySolicitudPagoIdOrderByCreadoEnAsc(Long solicitudPagoId);
    List<PagoSolicitudDetalle> findByPagoIdOrderByCreadoEnAsc(Long pagoId);

    /** true si el movimiento es el asiento consolidado de un evento de pago. */
    boolean existsByMovimientoCajaVirtualId(Long movimientoCajaVirtualId);

    /** Un detalle del evento de pago que posteo el movimiento: todos los que lo comparten son del mismo pago. */
    Optional<PagoSolicitudDetalle> findFirstByMovimientoBancarioId(Long movimientoBancarioId);
}
