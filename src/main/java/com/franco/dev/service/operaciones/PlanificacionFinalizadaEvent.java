package com.franco.dev.service.operaciones;

import lombok.Getter;

/**
 * Se finalizó la planificación (etapa CREACION) de un pedido. Lo publica {@link PedidoService#finalizarCreacion}.
 *
 * Lo escucha compras en el celular para cargar la factura que creó el pedido, una vez confirmada la transacción.
 */
@Getter
public class PlanificacionFinalizadaEvent {

    private final Long pedidoId;

    public PlanificacionFinalizadaEvent(Long pedidoId) {
        this.pedidoId = pedidoId;
    }
}
