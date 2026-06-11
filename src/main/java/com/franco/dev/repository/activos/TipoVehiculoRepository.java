package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.TipoVehiculo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TipoVehiculoRepository extends HelperRepository<TipoVehiculo, Long> {

    default Class<TipoVehiculo> getEntityClass() {
        return TipoVehiculo.class;
    }

    @Query("select t from TipoVehiculo t where CAST(t.id as text) like %?1% or UPPER(t.descripcion) like %?1%")
    public List<TipoVehiculo> findByAll(String texto);

    @Query("select t from TipoVehiculo t where CAST(t.id as text) like %?1% or UPPER(t.descripcion) like %?1%")
    public Page<TipoVehiculo> findByAllWithPage(String texto, Pageable pageable);

}
