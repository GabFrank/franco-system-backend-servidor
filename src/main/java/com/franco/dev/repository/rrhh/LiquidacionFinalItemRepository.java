package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LiquidacionFinalItemRepository extends HelperRepository<LiquidacionFinalItem, Long> {

    default Class<LiquidacionFinalItem> getEntityClass() {
        return LiquidacionFinalItem.class;
    }

    List<LiquidacionFinalItem> findByLiquidacionFinalIdOrderByIdAsc(Long liquidacionFinalId);

    /**
     * Suma ya cobrada de una cuota de venta a credito (CREDITO_CONVENIO_CUOTA) por los
     * finiquitos no ANULADOS, excluyendo la liquidacion final en curso.
     */
    @Query("select coalesce(sum(i.monto), 0) from LiquidacionFinalItem i " +
            "where i.referenciaTipo = 'CREDITO_CONVENIO_CUOTA' " +
            "and i.referenciaId = :cuotaId and i.referenciaSucursalId = :sucId " +
            "and i.liquidacionFinal.estado <> com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado.ANULADA " +
            "and (:excludeLiqId is null or i.liquidacionFinal.id <> :excludeLiqId)")
    BigDecimal sumConvenioCobrado(@Param("cuotaId") Long cuotaId,
                                  @Param("sucId") Long sucId,
                                  @Param("excludeLiqId") Long excludeLiqId);
}
