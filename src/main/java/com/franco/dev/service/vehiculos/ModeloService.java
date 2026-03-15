package com.franco.dev.service.vehiculos;

import com.franco.dev.domain.vehiculos.Modelo;
import com.franco.dev.repository.vehiculos.ModeloRepository;
import com.franco.dev.repository.vehiculos.VehiculoRepository;
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
public class ModeloService extends CrudService<Modelo, ModeloRepository, Long> {

    private final ModeloRepository repository;
    private final VehiculoRepository vehiculoRepository;

    @Override
    public ModeloRepository getRepository() {
        return repository;
    }

    public List<Modelo> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public Page<Modelo> findByAllWithPage(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable);
    }

    public Page<Modelo> findByMarcaIdAndTexto(Long marcaId, String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByMarcaIdAndTexto(marcaId, texto, pageable);
    }

    public List<Modelo> findByMarcaId(Long marcaId) {
        return repository.findByMarcaId(marcaId);
    }

    @Override
    public Modelo save(Modelo entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getDescripcion() != null) {
            entity.setDescripcion(entity.getDescripcion().toUpperCase());
        }
        Modelo e = super.save(entity);
        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        // Validación: no eliminar si hay vehículos asociados
        List<com.franco.dev.domain.vehiculos.Vehiculo> vehiculos = vehiculoRepository.findByModeloId(id);
        if(vehiculos != null && !vehiculos.isEmpty()) {
            throw new RuntimeException("No se puede eliminar el modelo porque tiene vehículos asociados");
        }
        return super.deleteById(id);
    }
}

