package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventarioProductoInput {
    private Long id;
    private Long zonaId;
    private Boolean concluido;
    private Long inventarioId;
    private Long productoId;
    private Long usuarioId;
}
