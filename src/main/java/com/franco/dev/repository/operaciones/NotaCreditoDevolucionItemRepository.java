package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaCreditoDevolucionItem;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface NotaCreditoDevolucionItemRepository extends HelperRepository<NotaCreditoDevolucionItem, Long> {

    default Class<NotaCreditoDevolucionItem> getEntityClass() {
        return NotaCreditoDevolucionItem.class;
    }

    List<NotaCreditoDevolucionItem> findByNotaCreditoId(Long notaCreditoId);

    void deleteByNotaCreditoId(Long notaCreditoId);
}
