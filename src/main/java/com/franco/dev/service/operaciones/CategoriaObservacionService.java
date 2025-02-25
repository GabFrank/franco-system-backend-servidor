package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.CategoriaObservacion;
import com.franco.dev.repository.operaciones.CategoriaObservacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class CategoriaObservacionService extends CrudService<CategoriaObservacion, CategoriaObservacionRepository, Long> {

    private final CategoriaObservacionRepository repository;

    @Override
    public CategoriaObservacionRepository getRepository() {
        return repository;
    }

    public List<CategoriaObservacion> findByIdOrDesc(Long id, String texto) {
        System.out.println("ID: " + id + ", Texto: " + texto);
        if (repository == null) {
            throw new IllegalStateException("Repository is not properly injected!");
        }
        return repository.findByIdOrDesc(id, texto);
    }

    @Override
    @Transactional
    public CategoriaObservacion save(CategoriaObservacion entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        CategoriaObservacion e = super.save(entity);
        return  e;
    }

}
