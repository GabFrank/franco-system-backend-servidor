package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface LiquidacionFinalItemRepository extends HelperRepository<LiquidacionFinalItem, Long> {

    default Class<LiquidacionFinalItem> getEntityClass() {
        return LiquidacionFinalItem.class;
    }

    List<LiquidacionFinalItem> findByLiquidacionFinalIdOrderByIdAsc(Long liquidacionFinalId);
}
