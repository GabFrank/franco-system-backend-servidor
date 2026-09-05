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
     * Items ya emitidos que usan un codigo del catalogo. {@code liquidacion_item.codigo}
     * es un String, no una FK, asi que la base no impide borrar un concepto en uso: los
     * recibos historicos se quedarian sin etiqueta y el codigo dejaria de resolver a
     * remunerativo/no remunerativo. Lo chequea LiquidacionConceptoGraphQL antes de borrar.
     */
    Long countByCodigo(String codigo);

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

    /**
     * Lo percibido por el funcionario en el anio, agregado por periodo, contando solo los
     * items HABER cuyo concepto es remunerativo.
     *
     * <p>Devuelve {@code [periodo, monto]} por mes. Una sola query: la version por item
     * haria un SELECT al catalogo por fila, que es el N+1 que ya se corrigio en el
     * recibo.</p>
     *
     * <p>El join al catalogo es por codigo (es un String, no una FK) y va por izquierda a
     * proposito: un item con un codigo que no esta en el catalogo cuenta como
     * remunerativo, igual que el DEFAULT TRUE de la columna. Asi los items historicos no
     * desaparecen de la base por no estar seedeados.</p>
     */
    @Query("select l.periodo, coalesce(sum(i.monto), 0) " +
            "from LiquidacionItem i " +
            "join i.liquidacion l " +
            "left join com.franco.dev.domain.rrhh.LiquidacionConcepto c on c.codigo = i.codigo " +
            "where l.funcionario.id = :funcionarioId " +
            "and l.periodo like concat(cast(:anio as string), '-%') " +
            "and (l.estado = com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado.APROBADA " +
            "  or l.estado = com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado.PAGADA) " +
            "and i.tipo = com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo.HABER " +
            "and (c.id is null or c.esRemunerativo is null or c.esRemunerativo = true) " +
            "group by l.periodo order by l.periodo")
    List<Object[]> percibidoPorPeriodo(@Param("funcionarioId") Long funcionarioId,
                                       @Param("anio") Integer anio);

    /**
     * Lo mismo que {@link #percibidoPorPeriodo}, pero sin filtro de anio y del mas
     * reciente al mas viejo. Lo usa el salario promedio del finiquito, que mira los
     * ultimos N meses aunque crucen el fin de anio.
     */
    @Query("select l.periodo, coalesce(sum(i.monto), 0) " +
            "from LiquidacionItem i " +
            "join i.liquidacion l " +
            "left join com.franco.dev.domain.rrhh.LiquidacionConcepto c on c.codigo = i.codigo " +
            "where l.funcionario.id = :funcionarioId " +
            "and (l.estado = com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado.APROBADA " +
            "  or l.estado = com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado.PAGADA) " +
            "and i.tipo = com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo.HABER " +
            "and (c.id is null or c.esRemunerativo is null or c.esRemunerativo = true) " +
            "group by l.periodo order by l.periodo desc")
    List<Object[]> percibidoPorPeriodoDesc(@Param("funcionarioId") Long funcionarioId);
}
