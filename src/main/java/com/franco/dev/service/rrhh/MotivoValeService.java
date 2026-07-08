package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.MotivoVale;
import com.franco.dev.repository.rrhh.MotivoValeRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MotivoValeService extends CrudService<MotivoVale, MotivoValeRepository, Long> {

    private final MotivoValeRepository repository;

    @Override
    public MotivoValeRepository getRepository() {
        return repository;
    }

    public List<MotivoVale> findActivos() {
        return repository.findByActivoTrueOrderByNombreAsc();
    }

    @Override
    public MotivoVale save(MotivoVale entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getActivo() == null) entity.setActivo(true);
        if (entity.getNombre() != null) entity.setNombre(entity.getNombre().toUpperCase());
        if (entity.getDescripcion() != null) entity.setDescripcion(entity.getDescripcion().toUpperCase());
        return super.save(entity);
    }
}
