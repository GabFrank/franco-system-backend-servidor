package com.franco.dev.repository.vehiculos;

import com.franco.dev.domain.vehiculos.VehiculoSucursal;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = "select * from vehiculos.vehiculo_sucursal " +
           "where (cast(:sucursalId as bigint) is null or sucursal_id = cast(:sucursalId as bigint)) " +
           "and (cast(:responsableId as bigint) is null or responsable_id = cast(:responsableId as bigint)) " +
           "order by creado_en desc",
           countQuery = "select count(*) from vehiculos.vehiculo_sucursal " +
           "where (cast(:sucursalId as bigint) is null or sucursal_id = cast(:sucursalId as bigint)) " +
           "and (cast(:responsableId as bigint) is null or responsable_id = cast(:responsableId as bigint))",
           nativeQuery = true)
    public Page<VehiculoSucursal> findAllBySucursalAndResponsable(@org.springframework.data.repository.query.Param("sucursalId") Long sucursalId, @org.springframework.data.repository.query.Param("responsableId") Long responsableId, Pageable pageable);

}

