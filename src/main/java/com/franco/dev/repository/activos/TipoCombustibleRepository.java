package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.TipoCombustible;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TipoCombustibleRepository extends HelperRepository<TipoCombustible, Long> {

    default Class<TipoCombustible> getEntityClass() {
        return TipoCombustible.class;
    }

    @Query("select t from TipoCombustible t where CAST(t.id as text) like %?1% or UPPER(t.descripcion) like %?1%")
    List<TipoCombustible> findByAll(String texto);
}
