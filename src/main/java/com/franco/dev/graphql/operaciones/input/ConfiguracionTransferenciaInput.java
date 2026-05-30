package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class ConfiguracionTransferenciaInput {
    private Long id;
    private Boolean permitirStockNegativo;
    private Long usuarioId;
}
