package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.PagoDetalle;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface PagoDetalleRepository extends HelperRepository<PagoDetalle, Long> {
    default Class<PagoDetalle> getEntityClass() {
        return PagoDetalle.class;
    }
    
    List<PagoDetalle> findByPagoId(Long pagoId);
}

