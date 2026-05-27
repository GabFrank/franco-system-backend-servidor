package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EnteVinculacionInput {
    private Long id;
    private Long enteId;
    private Long sucursalId;
    private Boolean esPropio;
    private Long alquilerProveedorId;
    private BigDecimal alquilerMonto;
    private Integer alquilerDiaVencimiento;
    private String alquilerVigencia;
    private String observacion;
    private Long usuarioId;
}
