package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class PdvCajaSumarioDto {
    private Double totalVentaGs;
    private Double totalVentaRs;
    private Double totalVentaDs;
    private Double totalTarjeta;
    private Double totalConvenio;
    private Double totalDescuento;
    private Double totalAumento;
    private Double vueltoGs;
    private Double vueltoRs;
    private Double vueltoDs;
    private Double totalGeneral;
}
