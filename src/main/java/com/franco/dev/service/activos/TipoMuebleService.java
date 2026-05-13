package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.TipoMueble;
import com.franco.dev.repository.activos.MuebleRepository;
import com.franco.dev.repository.activos.TipoMuebleRepository;
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
public class TipoMuebleService extends CrudService<TipoMueble, TipoMuebleRepository, Long> {

    private final TipoMuebleRepository repository;
    private final MuebleRepository muebleRepository;

    @Override
    public TipoMuebleRepository getRepository() {
        return repository;
    }

    public List<TipoMueble> findByAll(String texto) {
        if (texto == null) texto = "";
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public Page<TipoMueble> findByAllWithPage(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable);
    }

    public List<TipoMueble> findByFamiliaMuebleId(Long familiaMuebleId) {
        return repository.findByFamiliaMuebleId(familiaMuebleId);
    }

    @Override
    public TipoMueble save(TipoMueble entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        if (entity.getDescripcion() != null) {
            entity.setDescripcion(entity.getDescripcion().toUpperCase());
        }
        return super.save(entity);
    }

    @Override
    public Boolean deleteById(Long id) {
        List<com.franco.dev.domain.activos.Mueble> muebles = muebleRepository.findByTipoMuebleId(id);
        if (muebles != null && !muebles.isEmpty()) {
            throw new RuntimeException("No se puede eliminar el tipo de mueble porque tiene muebles asociados");
        }
        return super.deleteById(id);
    }
}
