package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.PagoDetalle;
import com.franco.dev.repository.operaciones.PagoDetalleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PagoDetalleService extends CrudService<PagoDetalle, PagoDetalleRepository, Long> {
    private final PagoDetalleRepository repository;

    @Override
    public PagoDetalleRepository getRepository() {
        return repository;
    }

    @Override
    public PagoDetalle save(PagoDetalle entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setActivo(true);
        }
        return super.save(entity);
    }
    
    public List<PagoDetalle> findByPagoId(Long pagoId) {
        return repository.findByPagoId(pagoId);
    }

    public Long getSucursalIdForPagoDetalle(Long pagoDetalleId) {
        // Using a native query to get the sucursal_id directly from the pago_detalle table
        return repository.findSucursalIdByPagoDetalleId(pagoDetalleId);
    }
}

