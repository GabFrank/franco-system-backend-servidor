package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PagoSolicitudDetalleRepository extends JpaRepository<PagoSolicitudDetalle, Long> {
    List<PagoSolicitudDetalle> findBySolicitudPagoIdOrderByCreadoEnAsc(Long solicitudPagoId);
}
