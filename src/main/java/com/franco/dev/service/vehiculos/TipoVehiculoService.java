package com.franco.dev.service.vehiculos;

import com.franco.dev.domain.vehiculos.TipoVehiculo;
import com.franco.dev.repository.vehiculos.TipoVehiculoRepository;
import com.franco.dev.repository.vehiculos.VehiculoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TipoVehiculoService extends CrudService<TipoVehiculo, TipoVehiculoRepository, Long> {

    private final TipoVehiculoRepository repository;
    private final VehiculoRepository vehiculoRepository;

    @Override
    public TipoVehiculoRepository getRepository() {
        return repository;
    }

    public List<TipoVehiculo> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    @Override
    public TipoVehiculo save(TipoVehiculo entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getDescripcion() != null) {
            entity.setDescripcion(entity.getDescripcion().toUpperCase());
        }
        TipoVehiculo e = super.save(entity);
        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        // Validación: no eliminar si hay vehículos asociados
        List<com.franco.dev.domain.vehiculos.Vehiculo> vehiculos = vehiculoRepository.findByTipoVehiculoId(id);
        if(vehiculos != null && !vehiculos.isEmpty()) {
            throw new RuntimeException("No se puede eliminar el tipo de vehículo porque tiene vehículos asociados");
        }
        return super.deleteById(id);
    }
}

