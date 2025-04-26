package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaSubCategoriaObservacion;
import com.franco.dev.repository.financiero.CajaSubCategoriaObservacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class CajaSubCategoriaObservacionService extends CrudService<CajaSubCategoriaObservacion, CajaSubCategoriaObservacionRepository, Long> {

    @Autowired
    private final CajaSubCategoriaObservacionRepository repository;

    @Override
    public CajaSubCategoriaObservacionRepository getRepository() {
        return repository;
    }

    public List<CajaSubCategoriaObservacion> findByCajaSubCategoriaIdOrDesc(Long id, String texto) {
        return repository.findByCajaSubCategoriaIdOrDesc(id, texto);
    }

    @Override
    public CajaSubCategoriaObservacion save(CajaSubCategoriaObservacion entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        CajaSubCategoriaObservacion e = super.save(entity);
        return e;
    }
}
