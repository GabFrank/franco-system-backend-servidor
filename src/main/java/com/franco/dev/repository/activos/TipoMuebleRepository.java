package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.TipoMueble;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TipoMuebleRepository extends HelperRepository<TipoMueble, Long> {

    default Class<TipoMueble> getEntityClass() {
        return TipoMueble.class;
    }

    @Query("select t from TipoMueble t where CAST(t.id as text) like %?1% or UPPER(t.descripcion) like %?1%")
    List<TipoMueble> findByAll(String texto);

    @Query("select t from TipoMueble t where CAST(t.id as text) like %?1% or UPPER(t.descripcion) like %?1%")
    Page<TipoMueble> findByAllWithPage(String texto, Pageable pageable);

    List<TipoMueble> findByFamiliaMuebleId(Long familiaMuebleId);
}
