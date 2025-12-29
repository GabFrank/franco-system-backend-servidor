package com.franco.dev.repository.vehiculos;

import com.franco.dev.domain.vehiculos.TipoVehiculo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TipoVehiculoRepository extends HelperRepository<TipoVehiculo, Long> {

    default Class<TipoVehiculo> getEntityClass() {
        return TipoVehiculo.class;
    }

    @Query("select t from TipoVehiculo t where CAST(t.id as text) like %?1% or UPPER(t.descripcion) like %?1%")
    public List<TipoVehiculo> findByAll(String texto);

}

