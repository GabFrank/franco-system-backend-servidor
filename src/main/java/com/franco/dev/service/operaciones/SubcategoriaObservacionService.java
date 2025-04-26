package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.SubcategoriaObservacion;
import com.franco.dev.repository.operaciones.SubcategoriaObservacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class SubcategoriaObservacionService extends CrudService<SubcategoriaObservacion, SubcategoriaObservacionRepository, Long> {
    @Autowired
    private final SubcategoriaObservacionRepository repository;

    @Override
    public SubcategoriaObservacionRepository getRepository() { return repository; }

    public List<SubcategoriaObservacion> findBySubcategoriaIdOrDesc (Long id, String texto) {
        return repository.findBySubcategoriaIdOrDesc(id, texto);
    }

    @Override
    public SubcategoriaObservacion save(SubcategoriaObservacion entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        SubcategoriaObservacion e = super.save(entity);
        return e;
    }
}
