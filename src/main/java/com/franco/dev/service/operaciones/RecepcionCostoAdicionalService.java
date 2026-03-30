package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.RecepcionCostoAdicional;
import com.franco.dev.repository.operaciones.RecepcionCostoAdicionalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class RecepcionCostoAdicionalService extends CrudService<RecepcionCostoAdicional, RecepcionCostoAdicionalRepository, Long> {
    
    private final RecepcionCostoAdicionalRepository repository;

    @Override
    public RecepcionCostoAdicionalRepository getRepository() {
        return repository;
    }

    /**
     * Busca costos adicionales por ID de recepción de mercadería
     */
    public List<RecepcionCostoAdicional> findByRecepcionMercaderiaId(Long recepcionId) {
        return repository.findByRecepcionMercaderiaId(recepcionId);
    }
} 