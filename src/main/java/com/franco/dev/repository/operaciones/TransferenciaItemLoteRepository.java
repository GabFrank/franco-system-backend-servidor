package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.TransferenciaItemLote;
import com.franco.dev.domain.operaciones.enums.EtapaAsignacionLote;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface TransferenciaItemLoteRepository extends HelperRepository<TransferenciaItemLote, Long> {

    default Class<TransferenciaItemLote> getEntityClass() {
        return TransferenciaItemLote.class;
    }

    List<TransferenciaItemLote> findByTransferenciaItemIdOrderByIdAsc(Long transferenciaItemId);

    List<TransferenciaItemLote> findByTransferenciaItemIdAndEtapaOrderByIdAsc(Long transferenciaItemId,
                                                                             EtapaAsignacionLote etapa);

    void deleteByTransferenciaItemIdAndEtapa(Long transferenciaItemId, EtapaAsignacionLote etapa);
}
