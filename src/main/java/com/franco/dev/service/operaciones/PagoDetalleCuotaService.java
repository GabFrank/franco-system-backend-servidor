package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.PagoDetalleCuota;
import com.franco.dev.domain.operaciones.enums.PagoDetalleCuotaEstado;
import com.franco.dev.repository.operaciones.PagoDetalleCuotaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PagoDetalleCuotaService extends CrudService<PagoDetalleCuota, PagoDetalleCuotaRepository, Long> {
    private final PagoDetalleCuotaRepository repository;

    @Override
    public PagoDetalleCuotaRepository getRepository() {
        return repository;
    }

    @Override
    public PagoDetalleCuota save(PagoDetalleCuota entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            if (entity.getEstado() == null) {
                entity.setEstado(PagoDetalleCuotaEstado.PENDIENTE);
            }
            if (entity.getTotalPagado() == null) {
                entity.setTotalPagado(0.0);
            }
        }
        return super.save(entity);
    }
    
    public List<PagoDetalleCuota> findByPagoDetalleId(Long pagoDetalleId) {
        return repository.findByPagoDetalleId(pagoDetalleId);
    }
    
    public List<PagoDetalleCuota> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }
} 