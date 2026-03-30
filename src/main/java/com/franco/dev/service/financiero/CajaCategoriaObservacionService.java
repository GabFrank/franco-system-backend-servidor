package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaCategoriaObservacion;
import com.franco.dev.repository.financiero.CajaCategoriaObservacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class CajaCategoriaObservacionService extends CrudService<CajaCategoriaObservacion, CajaCategoriaObservacionRepository, Long> {

    private final CajaCategoriaObservacionRepository repository;

    @Override
    public CajaCategoriaObservacionRepository getRepository() { return repository; }

    public List<CajaCategoriaObservacion> findByIdOrDesc(Long id, String texto) {
        System.out.println("ID: " + id + ", Texto: " + texto);
        if (repository == null) {
            throw new IllegalStateException("Repository is not properly injected!");
        }
        return repository.findByIdOrDesc(id, texto);
    }

    @Override
    @Transactional
    public CajaCategoriaObservacion save(CajaCategoriaObservacion entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        CajaCategoriaObservacion e = super.save(entity);
        return e;
    }
}
