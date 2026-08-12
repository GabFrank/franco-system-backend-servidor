package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class EntradaVariaInput {
    private String descripcion;
    private Double monto;
    private Boolean esIngreso;
    private Long cajaVirtualId;
    private Long monedaId;
    private Long categoriaId;
    private Long formaPagoId;
    private String numeroComprobante;
}
