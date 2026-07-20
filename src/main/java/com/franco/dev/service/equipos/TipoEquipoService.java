package com.franco.dev.service.equipos;

import com.franco.dev.domain.equipos.TipoEquipo;
import com.franco.dev.graphql.equipos.dto.TipoEquipoOutput;
import com.franco.dev.repository.equipos.TipoEquipoRepository;
import com.franco.dev.service.CrudService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TipoEquipoService extends CrudService<TipoEquipo, TipoEquipoRepository, Long> {

    private final TipoEquipoRepository repository;

    public TipoEquipoService(TipoEquipoRepository repository) {
        this.repository = repository;
    }

    @Override
    public TipoEquipoRepository getRepository() {
        return repository;
    }

    public List<TipoEquipoOutput> buscar(String texto) {
        if (texto == null) {
            texto = "";
        }
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase()).stream()
                .map(this::aOutput)
                .collect(Collectors.toList());
    }

    public Page<TipoEquipoOutput> buscarConPagina(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable).map(this::aOutput);
    }

    public TipoEquipoOutput aOutput(TipoEquipo entity) {
        if (entity == null) {
            return null;
        }
        TipoEquipoOutput output = new TipoEquipoOutput();
        output.setId(entity.getId());
        output.setDescripcion(entity.getDescripcion());
        output.setSucursal(entity.getSucursal());
        output.setUsuario(entity.getUsuario());
        output.setCreadoEn(entity.getCreadoEn());
        return output;
    }

    @Override
    public TipoEquipo save(TipoEquipo entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }
}
