package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class ConfiguracionVentaTarjetaInput {
    private Long id;
    private Boolean habilitado;
    private Long usuarioId;
}
