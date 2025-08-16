package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EventoDte;
import com.franco.dev.repository.HelperRepository;
import java.util.List;

public interface EventoDteRepository extends HelperRepository<EventoDte, Long> {
    default Class<EventoDte> getEntityClass() {
        return EventoDte.class;
    }

    List<EventoDte> findByDocumentoElectronicoIdOrderByIdAsc(Long documentoElectronicoId);
}


