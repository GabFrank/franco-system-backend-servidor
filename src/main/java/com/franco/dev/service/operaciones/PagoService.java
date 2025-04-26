package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.enums.PagoEstado;
import com.franco.dev.repository.operaciones.PagoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class PagoService extends CrudService<Pago, PagoRepository, Long> {
    private final PagoRepository repository;

    @Override
    public PagoRepository getRepository() {
        return repository;
    }

    @Override
    public Pago save(Pago entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setEstado(PagoEstado.ABIERTO);
        }
        return super.save(entity);
    }
    
    public Pago findBySolicitudPagoId(Long solicitudPagoId) {
        return repository.findBySolicitudPagoId(solicitudPagoId);
    }
}

