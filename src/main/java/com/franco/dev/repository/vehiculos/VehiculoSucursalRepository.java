package com.franco.dev.repository.vehiculos;

import com.franco.dev.domain.vehiculos.VehiculoSucursal;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VehiculoSucursalRepository extends HelperRepository<VehiculoSucursal, Long> {

    default Class<VehiculoSucursal> getEntityClass() {
        return VehiculoSucursal.class;
    }

    public List<VehiculoSucursal> findByVehiculoId(Long vehiculoId);

    public List<VehiculoSucursal> findBySucursalId(Long sucursalId);

    @Query("select vs from VehiculoSucursal vs where vs.vehiculo.id = ?1 and vs.sucursal.id = ?2")
    public List<VehiculoSucursal> findByVehiculoIdAndSucursalId(Long vehiculoId, Long sucursalId);

}

