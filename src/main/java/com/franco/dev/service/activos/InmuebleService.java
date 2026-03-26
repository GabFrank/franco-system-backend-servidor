package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Inmueble;
import com.franco.dev.repository.activos.InmuebleRepository;
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
public class InmuebleService extends CrudService<Inmueble, InmuebleRepository, Long> {

    private final InmuebleRepository repository;

    @Override
    public InmuebleRepository getRepository() {
        return repository;
    }

    public List<Inmueble> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public Page<Inmueble> findByAllWithPage(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable);
    }

    public List<Inmueble> findByPropietarioId(Long propietarioId) {
        return repository.findByPropietarioId(propietarioId);
    }

    public List<Inmueble> findByPaisId(Long paisId) {
        return repository.findByPaisId(paisId);
    }

    public List<Inmueble> findByCiudadId(Long ciudadId) {
        return repository.findByCiudadId(ciudadId);
    }

    @Override
    public Inmueble save(Inmueble entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }
}
