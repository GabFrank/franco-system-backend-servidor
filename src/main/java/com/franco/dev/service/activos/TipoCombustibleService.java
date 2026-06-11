package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.TipoCombustible;
import com.franco.dev.repository.activos.TipoCombustibleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TipoCombustibleService extends CrudService<TipoCombustible, TipoCombustibleRepository, Long> {

    private final TipoCombustibleRepository repository;

    @Override
    public TipoCombustibleRepository getRepository() {
        return repository;
    }

    public List<TipoCombustible> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    @Override
    public TipoCombustible save(TipoCombustible entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        if (entity.getDescripcion() != null) {
            entity.setDescripcion(entity.getDescripcion().toUpperCase());
        }
        return super.save(entity);
    }
}
