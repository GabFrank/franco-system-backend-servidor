package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItemVariacion;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaItemVariacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class RecepcionMercaderiaItemVariacionService extends CrudService<RecepcionMercaderiaItemVariacion, RecepcionMercaderiaItemVariacionRepository, Long> {

    private final RecepcionMercaderiaItemVariacionRepository repository;

    @Override
    public RecepcionMercaderiaItemVariacionRepository getRepository() {
        return repository;
    }

    public List<RecepcionMercaderiaItemVariacion> findByRecepcionMercaderiaItemId(Long id) {
        return repository.findByRecepcionMercaderiaItemId(id);
    }

    @Transactional
    public void deleteByRecepcionMercaderiaItemId(Long id){
        repository.deleteByRecepcionMercaderiaItemId(id);
    }
}
