package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface LiquidacionItemRepository extends HelperRepository<LiquidacionItem, Long> {

    default Class<LiquidacionItem> getEntityClass() {
        return LiquidacionItem.class;
    }

    List<LiquidacionItem> findByLiquidacionIdOrderByIdAsc(Long liquidacionId);

    List<LiquidacionItem> findByLiquidacionIdAndManualFalse(Long liquidacionId);
}
