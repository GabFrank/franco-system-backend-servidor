package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SucursalRepository extends HelperRepository<Sucursal, Long> {

    default Class<Sucursal> getEntityClass() {
        return Sucursal.class;
    }

    List<Sucursal> findAllByOrderByIdAsc();

    @Query("select p from Sucursal p where p.id != 0 and (cast(?1 as text) is null or (CAST(id as text) like %?1% or UPPER(p.nombre) like %?1%)) order by p.id asc")
    public List<Sucursal> findByAll(String texto);

    public List<Sucursal> findByIsConfiguredFalse();

//    public List<Pais> findBySubFamiliaId(Long id);

}