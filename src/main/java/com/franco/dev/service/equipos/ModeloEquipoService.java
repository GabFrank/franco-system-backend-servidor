package com.franco.dev.service.equipos;

import com.franco.dev.domain.equipos.ModeloEquipo;
import com.franco.dev.graphql.equipos.dto.ModeloEquipoOutput;
import com.franco.dev.repository.equipos.EquipoRepository;
import com.franco.dev.repository.equipos.ModeloEquipoRepository;
import com.franco.dev.service.CrudService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ModeloEquipoService extends CrudService<ModeloEquipo, ModeloEquipoRepository, Long> {

    private final ModeloEquipoRepository repository;
    private final MarcaEquipoService marcaEquipoService;
    private final EquipoRepository equipoRepository;

    public ModeloEquipoService(
            ModeloEquipoRepository repository,
            MarcaEquipoService marcaEquipoService,
            EquipoRepository equipoRepository) {
        this.repository = repository;
        this.marcaEquipoService = marcaEquipoService;
        this.equipoRepository = equipoRepository;
    }

    @Override
    public ModeloEquipoRepository getRepository() {
        return repository;
    }

    public Page<ModeloEquipoOutput> buscarConPagina(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable).map(this::aOutput);
    }

    public ModeloEquipoOutput aOutput(ModeloEquipo entity) {
        if (entity == null) {
            return null;
        }
        ModeloEquipoOutput output = new ModeloEquipoOutput();
        output.setId(entity.getId());
        output.setDescripcion(entity.getDescripcion());
        output.setMarca(marcaEquipoService.aOutput(entity.getMarca()));
        output.setUsuario(entity.getUsuario());
        output.setCreadoEn(entity.getCreadoEn());
        return output;
    }

    @Override
    public ModeloEquipo save(ModeloEquipo entity) {
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
        List<com.franco.dev.domain.equipos.Equipo> equipos = equipoRepository.findByModeloId(id);
        if (equipos != null && !equipos.isEmpty()) {
            throw new RuntimeException("No se puede eliminar el modelo porque tiene equipos asociados");
        }
        return super.deleteById(id);
    }
}
