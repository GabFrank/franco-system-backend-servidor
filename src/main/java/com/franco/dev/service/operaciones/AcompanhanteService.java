package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Acompanhante;
import com.franco.dev.repository.operaciones.AcompanhanteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class AcompanhanteService
        extends CrudService<Acompanhante, AcompanhanteRepository, Acompanhante.AcompanhanteId> {

    private final AcompanhanteRepository repository;

    @Override
    public AcompanhanteRepository getRepository() {
        return repository;
    }

    public List<Acompanhante> findByHojaRutaId(Long hojaRutaId) {
        return repository.findByHojaRutaId(hojaRutaId);
    }
}
