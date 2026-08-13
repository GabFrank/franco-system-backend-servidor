package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.ModoAjusteLote;
import lombok.Data;

/**
 * Un ajuste de stock sobre UN lote en UNA sucursal.
 *
 * El lote se identifica por {@code loteId} cuando ya existe, o por {@code numeroLote} cuando el
 * operador lo está dando de alta desde la misma pantalla. Es el único camino de alta manual de
 * lote que tiene el sistema: sin él no se puede trazar mercadería que ya estaba en góndola antes
 * de que el producto tuviera control de lote.
 */
@Data
public class AjusteStockLoteInput {

    private Long productoId;

    private Long sucursalId;

    private ModoAjusteLote modo;

    /**
     * Cuántas unidades de ESE lote hay realmente, EN UNIDADES BASE. No es la diferencia: es el
     * total contado. El backend calcula la diferencia contra el saldo actual, igual que el ajuste
     * de stock de siempre, para que el operador nunca tenga que restar de cabeza.
     */
    private Double cantidadFinal;

    /** Por qué se ajusta. Obligatorio: es lo que hace auditable un ajuste sobre stock trazado. */
    private String motivo;

    private Long usuarioId;

    /** Lote existente. Excluyente con {@link #numeroLote}. */
    private Long loteId;

    /** Número del lote a resolver o crear. Se normaliza igual que en la recepción. */
    private String numeroLote;

    /** Solo se usa al crear el lote. Formato yyyy-MM-dd. */
    private String fechaVencimiento;

    /**
     * Solo se usa al crear el lote. Si no viene, se deriva de los días de vencimiento del producto,
     * exactamente como en la recepción.
     */
    private String fechaRetiro;
}
