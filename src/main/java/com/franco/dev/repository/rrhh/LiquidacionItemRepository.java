package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LiquidacionItemRepository extends HelperRepository<LiquidacionItem, Long> {

    default Class<LiquidacionItem> getEntityClass() {
        return LiquidacionItem.class;
    }

    List<LiquidacionItem> findByLiquidacionIdOrderByIdAsc(Long liquidacionId);

    List<LiquidacionItem> findByLiquidacionIdAndManualFalse(Long liquidacionId);

    /**
     * Suma ya cobrada de una cuota de venta a credito (CREDITO_CONVENIO_CUOTA) por las
     * liquidaciones mensuales no ANULADAS, excluyendo la liquidacion en curso.
     */
    @Query("select coalesce(sum(i.monto), 0) from LiquidacionItem i " +
            "where i.referenciaTipo = 'CREDITO_CONVENIO_CUOTA' " +
            "and i.referenciaId = :cuotaId and i.referenciaSucursalId = :sucId " +
            "and i.liquidacion.estado <> com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado.ANULADA " +
            "and (:excludeLiqId is null or i.liquidacion.id <> :excludeLiqId)")
    BigDecimal sumConvenioCobrado(@Param("cuotaId") Long cuotaId,
                                  @Param("sucId") Long sucId,
                                  @Param("excludeLiqId") Long excludeLiqId);
}
