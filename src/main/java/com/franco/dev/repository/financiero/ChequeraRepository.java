package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChequeraRepository extends HelperRepository<Chequera, Long> {
    default Class<Chequera> getEntityClass() {
        return Chequera.class;
    }

    @Query("select c from Chequera c " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(CAST(rangoDesde as text)) like %?1% or UPPER(CAST(rangoHasta as text)) like %?1%")
    public List<Chequera> findByAll(String texto);
} 