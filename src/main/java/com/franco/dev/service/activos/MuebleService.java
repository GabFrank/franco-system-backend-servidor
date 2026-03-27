package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Mueble;
import com.franco.dev.repository.activos.MuebleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MuebleService extends CrudService<Mueble, MuebleRepository, Long> {

    private final MuebleRepository repository;

    @Override
    public MuebleRepository getRepository() {
        return repository;
    }

    public List<Mueble> findByAll(String texto) {
        if (texto == null) texto = "";
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public Page<Mueble> findByAllWithPage(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable);
    }

    public List<Mueble> findByFamiliaId(Long familiaId) {
        return repository.findByFamiliaId(familiaId);
    }

    public List<Mueble> findByTipoMuebleId(Long tipoMuebleId) {
        return repository.findByTipoMuebleId(tipoMuebleId);
    }

    public List<Mueble> findByPropietarioId(Long propietarioId) {
        return repository.findByPropietarioId(propietarioId);
    }

    @Override
    public Mueble save(Mueble entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }
}
