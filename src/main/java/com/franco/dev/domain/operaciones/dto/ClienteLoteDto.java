package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Una venta de un lote. El cliente viene resuelto cuando la venta lo identifica; en las de
 * mostrador es el genérico.
 *
 * Es la lista que se necesita en un recall: bloquear el lote lo saca del mostrador, pero avisar
 * exige saber a quién llamar.
 *
 * Va una fila por VENTA y no una por cliente, aunque eso repita al cliente que compró el lote
 * varias veces: cada fila tiene que poder abrir su venta, y un cliente agrupado no tiene un único
 * número al que apuntar.
 *
 * Una consulta devuelve las ventas rastreables o las de mostrador, nunca las dos juntas. La gran
 * mayoría de las ventas del sistema van contra el cliente genérico, así que mezcladas taparían a
 * los pocos clientes reales, que son justamente los que se pueden llamar.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClienteLoteDto {

    private Long ventaId;
    /** La clave de venta es (id, sucursal_id): sin la sucursal el número es ambiguo. */
    private Long sucursalId;
    private String sucursalNombre;
    private LocalDateTime fecha;
    private Long clienteId;
    private String clienteNombre;
    private String clienteDocumento;
    /** Unidades del lote que salieron en esa venta, en positivo: el ledger las guarda negativas. */
    private Double cantidad;
}
