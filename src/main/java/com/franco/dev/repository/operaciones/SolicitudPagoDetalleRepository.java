package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPagoDetalle;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SolicitudPagoDetalleRepository extends HelperRepository<SolicitudPagoDetalle, Long>, JpaSpecificationExecutor<SolicitudPagoDetalle> {

    default Class<SolicitudPagoDetalle> getEntityClass() {
        return SolicitudPagoDetalle.class;
    }

    List<SolicitudPagoDetalle> findBySolicitudPagoIdOrderByOrdenAscIdAsc(Long solicitudPagoId);

    void deleteBySolicitudPagoId(Long solicitudPagoId);
}
