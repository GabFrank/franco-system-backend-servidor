package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Marca;
import com.franco.dev.repository.activos.MarcaRepository;
import com.franco.dev.repository.activos.ModeloRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MarcaService extends CrudService<Marca, MarcaRepository, Long> {

    private final MarcaRepository repository;
    private final ModeloRepository modeloRepository;

    @Override
    public MarcaRepository getRepository() {
        return repository;
    }

    public List<Marca> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    @Override
    public Marca save(Marca entity) {
        if (entity.getId() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getDescripcion() != null) {
            entity.setDescripcion(entity.getDescripcion().toUpperCase());
        }
        Marca e = super.save(entity);
        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        // Validación: no eliminar si hay modelos asociados
        List<com.franco.dev.domain.activos.Modelo> modelos = modeloRepository.findByMarcaId(id);
        if (modelos != null && !modelos.isEmpty()) {
            throw new RuntimeException("No se puede eliminar la marca porque tiene modelos asociados");
        }
        return super.deleteById(id);
    }
}
