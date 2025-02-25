package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MotivoObservacion;
import com.franco.dev.repository.operaciones.MotivoObservacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MotivoObservacionService extends CrudService<MotivoObservacion, MotivoObservacionRepository, Long> {
    @Autowired
    private final MotivoObservacionRepository repository;

    @Override
    public MotivoObservacionRepository getRepository() { return repository; }

    public List<MotivoObservacion> findByMotivoIdOrDesc(Long id, String texto) {
        return repository.findByMotivoIdOrDesc(id, texto);
    }

    @Override
    public MotivoObservacion save(MotivoObservacion entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        MotivoObservacion e = super.save(entity);
        return e;
    }
}
