package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionConcepto;
import com.franco.dev.repository.HelperRepository;

import java.util.Optional;

public interface LiquidacionConceptoRepository extends HelperRepository<LiquidacionConcepto, Long> {

    default Class<LiquidacionConcepto> getEntityClass() {
        return LiquidacionConcepto.class;
    }

    Optional<LiquidacionConcepto> findByCodigo(String codigo);
}
