package com.franco.dev.service.equipos;

import com.franco.dev.domain.equipos.MarcaEquipo;
import com.franco.dev.graphql.equipos.dto.MarcaEquipoOutput;
import com.franco.dev.repository.equipos.MarcaEquipoRepository;
import com.franco.dev.repository.equipos.ModeloEquipoRepository;
import com.franco.dev.service.CrudService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MarcaEquipoService extends CrudService<MarcaEquipo, MarcaEquipoRepository, Long> {

    private final MarcaEquipoRepository repository;
    private final ModeloEquipoRepository modeloEquipoRepository;

    public MarcaEquipoService(MarcaEquipoRepository repository, ModeloEquipoRepository modeloEquipoRepository) {
        this.repository = repository;
        this.modeloEquipoRepository = modeloEquipoRepository;
    }

    @Override
    public MarcaEquipoRepository getRepository() {
        return repository;
    }

    public List<MarcaEquipo> buscar(String texto) {
        if (texto == null) {
            texto = "";
        }
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public MarcaEquipoOutput aOutput(MarcaEquipo entity) {
        if (entity == null) {
            return null;
        }
        MarcaEquipoOutput output = new MarcaEquipoOutput();
        output.setId(entity.getId());
        output.setDescripcion(entity.getDescripcion());
        output.setUsuario(entity.getUsuario());
        output.setCreadoEn(entity.getCreadoEn());
        return output;
    }

    @Override
    public MarcaEquipo save(MarcaEquipo entity) {
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
        List<com.franco.dev.domain.equipos.ModeloEquipo> modelos = modeloEquipoRepository.findByMarcaId(id);
        if (modelos != null && !modelos.isEmpty()) {
            throw new RuntimeException("No se puede eliminar la marca porque tiene modelos asociados");
        }
        return super.deleteById(id);
    }
}
