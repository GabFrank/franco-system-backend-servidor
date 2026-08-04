package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un movimiento del historial de un lote: de dónde vino y a dónde fue.
 *
 * Es la lectura inversa del ledger. {@code movimientoStockLotePorMovimiento} responde "qué lotes
 * tiene este movimiento"; esto responde "qué movimientos tocaron este lote", que es la pregunta
 * de un recall: bloquear el lote lo saca del mostrador, pero para avisar hay que saber a quién se
 * le vendió.
 *
 * OJO con {@code referencia}: no es el documento, es el ÍTEM del documento. El ledger se escribe
 * por ítem, así que para una venta apunta a {@code venta_item}, para una compra a
 * {@code recepcion_mercaderia_item} y para una transferencia a {@code transferencia_item}.
 * Mostrarla como "documento" es engañoso: el id no existe en la tabla que el operador iría a
 * buscar. Por eso viaja también {@code documentoId}, ya resuelto al padre.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MovimientoLoteDto {

    /**
     * Id de la fila del ledger. No es único por sí solo: la clave de
     * {@code movimiento_stock_lote} es (id, sucursal_id), por eso viaja junto a la sucursal.
     */
    private Long id;
    private Long sucursalId;
    private LocalDateTime fecha;
    private String sucursalNombre;
    /** COMPRA, VENTA, AJUSTE o TRANSFERENCIA. Nulo si el movimiento padre ya no está. */
    private String tipoMovimiento;
    /** Id del ÍTEM que originó el movimiento, no del documento. Ver el javadoc de la clase. */
    private Long referencia;
    /**
     * Documento al que pertenece ese ítem: la venta, la recepción o la transferencia. Nulo cuando
     * el tipo no tiene documento resoluble o cuando el ítem ya no está.
     */
    private Long documentoId;
    /** Positiva si entró al lote, negativa si salió. */
    private Double cantidad;
    private String usuarioNombre;
}
