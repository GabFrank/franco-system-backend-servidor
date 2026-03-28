package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.FamiliaMueble;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FamiliaMuebleRepository extends HelperRepository<FamiliaMueble, Long> {

    default Class<FamiliaMueble> getEntityClass() {
        return FamiliaMueble.class;
    }

    @Query("select f from FamiliaMueble f where CAST(f.id as text) like %?1% or UPPER(f.descripcion) like %?1%")
    List<FamiliaMueble> findByAll(String texto);

    @Query("select f from FamiliaMueble f where UPPER(CAST(f.id as text)) like UPPER(concat('%', ?1, '%')) or UPPER(f.descripcion) like UPPER(concat('%', ?1, '%'))")
    org.springframework.data.domain.Page<FamiliaMueble> findByAllWithPage(String texto, org.springframework.data.domain.Pageable pageable);
}
