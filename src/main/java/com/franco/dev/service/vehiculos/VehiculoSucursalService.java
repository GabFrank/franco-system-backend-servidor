package com.franco.dev.service.vehiculos;

import com.franco.dev.domain.vehiculos.VehiculoSucursal;
import com.franco.dev.repository.vehiculos.VehiculoSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class VehiculoSucursalService extends CrudService<VehiculoSucursal, VehiculoSucursalRepository, Long> {

    private final VehiculoSucursalRepository repository;

    @Override
    public VehiculoSucursalRepository getRepository() {
        return repository;
    }

    public List<VehiculoSucursal> findByVehiculoId(Long vehiculoId) {
        return repository.findByVehiculoId(vehiculoId);
    }

    public List<VehiculoSucursal> findBySucursalId(Long sucursalId) {
        return repository.findBySucursalId(sucursalId);
    }

    public List<VehiculoSucursal> findByVehiculoIdAndSucursalId(Long vehiculoId, Long sucursalId) {
        return repository.findByVehiculoIdAndSucursalId(vehiculoId, sucursalId);
    }

    public Page<VehiculoSucursal> findBySucursalAndResponsable(Long sucursalId, Long responsableId, Pageable pageable) {
        return repository.findAllBySucursalAndResponsable(sucursalId, responsableId, pageable);
    }

    @Override
    public VehiculoSucursal save(VehiculoSucursal entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        VehiculoSucursal e = super.save(entity);
        return e;
    }
}

