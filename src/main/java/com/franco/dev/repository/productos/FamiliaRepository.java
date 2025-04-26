package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FamiliaRepository extends HelperRepository<Familia, Long> {

    default Class<Familia> getEntityClass() {
        return Familia.class;
    }

    @Query("select f from Familia f where CAST(id as text) like %?1% or UPPER(descripcion) like %?1%")
    public List<Familia> findByAll(String texto);

    @Query("select f from Familia f where CAST(id as text) like %?1% or UPPER(descripcion) like %?1% or UPPER(nombre) like %?1% order by id asc")
    public Page<Familia> findByAllWithPage(String texto, Pageable pageable);

}
