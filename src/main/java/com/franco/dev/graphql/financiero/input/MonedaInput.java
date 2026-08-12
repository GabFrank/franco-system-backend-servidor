package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class MonedaInput {
    private Long id;
    private String denominacion;
    private String simbolo;
    private Boolean activo;
    private Boolean principal;
    private Integer decimales;
    private String reglaRedondeo;
    private java.math.BigDecimal redondeoMultiplo;
    private Long paisId;
    private Long usuarioId;
}
