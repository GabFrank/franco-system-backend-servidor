package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.repository.productos.FamiliaRepository;
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
public class FamiliaService extends CrudService<Familia, FamiliaRepository, Long> {

    private final FamiliaRepository repository;

    @Override
    public FamiliaRepository getRepository() {
        return repository;
    }

    public List<Familia> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto);
    }

    public Page<Familia> findByAllWithPage(String texto, int index, int size){
        Pageable page = PageRequest.of(index, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return  repository.findByAllWithPage(texto, page);
    }

    @Override
    public Familia save(Familia entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        Familia e = super.save(entity);
        return e;
    }
}
