package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EnteFinanciero;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EnteFinancieroRepository extends HelperRepository<EnteFinanciero, Long> {

    default Class<EnteFinanciero> getEntityClass() {
        return EnteFinanciero.class;
    }

    @Query("select ef from EnteFinanciero ef where ef.ente.id = ?1")
    Optional<EnteFinanciero> findByEnteId(Long enteId);

    @Query("select ef from EnteFinanciero ef left join ef.ente e left join ef.proveedor p where " +
            "CAST(ef.id as text) like %?1% or " +
            "UPPER(cast(ef.situacionPago as text)) like %?1% or " +
            "UPPER(p.nombre) like %?1%")
    Page<EnteFinanciero> findByAllWithPage(String texto, Pageable pageable);
}
