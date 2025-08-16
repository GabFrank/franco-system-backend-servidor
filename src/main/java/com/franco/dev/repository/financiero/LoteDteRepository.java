package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.LoteDte;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface LoteDteRepository extends HelperRepository<LoteDte, Long> {
    default Class<LoteDte> getEntityClass() {
        return LoteDte.class;
    }

    List<LoteDte> findByEstadoSifen(String estado);

    List<LoteDte> findTop10ByOrderByIdDesc();
}


