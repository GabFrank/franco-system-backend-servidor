package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.repository.productos.SubFamiliaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class SubFamiliaService extends CrudService<Subfamilia, SubFamiliaRepository, Long> {

    @Autowired
    private final SubFamiliaRepository repository;

    @Autowired
    private FamiliaService familiaService;

    @Override
    public SubFamiliaRepository getRepository() {
        return repository;
    }

    public Page<Subfamilia> findByDescripcion(Long familiaId, String texto, int page, int size) {
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByDescripcion(familiaId, texto, pageable);
    }

    public Page<Subfamilia> findByDescripcionSinFamilia(String texto, int page, int size) {
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByDescripcionSinFamilia(texto, pageable);
    }

    public List<Subfamilia> findByFamiliaDescripcion(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByFamiliaDescripcion(texto);
    }

    public List<Subfamilia> findByFamiliaId(Long id) {
        return repository.findByFamiliaId(id);
    }

    public List<Subfamilia> findBySubFamilia(Long id) {
        return repository.findBySubfamiliaId(id);
    }

    @Override
    public Subfamilia save(Subfamilia entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        Subfamilia e = super.save(entity);
        return e;
    }

    public List<Subfamilia> findBySubfamiliaIsNull() {
        return repository.findBySubfamiliaIsNull();
    }

}
