package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import lombok.Data;

@Data
public class PedidoItemSucursalInput {
    private Long id;
    private Long pedidoItemId;
    private Long sucursalId;
    private Long sucursalEntregaId;
    private Double cantidadPorUnidadRecibida;
    private Double cantidadPorUnidad;
    private Long usuarioId;
}
