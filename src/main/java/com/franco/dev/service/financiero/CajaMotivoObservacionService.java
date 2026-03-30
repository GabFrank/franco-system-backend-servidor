package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaMotivoObservacion;
import com.franco.dev.repository.financiero.CajaMotivoObservacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class CajaMotivoObservacionService extends CrudService<CajaMotivoObservacion, CajaMotivoObservacionRepository, Long> {
    @Autowired
    private final CajaMotivoObservacionRepository repository;

    @Override
    public CajaMotivoObservacionRepository getRepository() { return repository; }

    public List<CajaMotivoObservacion> findByCajaMotivoIdOrDesc(Long id, String texto) {
        return repository.findByCajaMotivoIdOrDesc(id, texto);
    }

    @Override
    public CajaMotivoObservacion save(CajaMotivoObservacion entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        CajaMotivoObservacion e = super.save(entity);
        return e;
    }
}