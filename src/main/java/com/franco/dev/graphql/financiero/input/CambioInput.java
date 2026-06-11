package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class CambioInput {
    private Long id;
    private Double valorEnGs;
    private Double valorEnGsCambio;
    private Double valorEnGsVentaMercado;
    private Double valorEnGsCompraMercado;
    private Boolean activo;
    private Long monedaId;
    private Long usuarioId;
}
