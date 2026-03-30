package com.franco.dev.service.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacturaLegalFilialResponse {
    private Long id;
    private Integer numeroFactura;
    private String nombre;
    private String ruc;
    private String direccion;
    private String fecha;
    private Double totalFinal;
    private Boolean esElectronica;
    private String cdc;
    private String urlQr;
    private String estadoDocumentoElectronico;
    private String mensajeRespuestaSifen;
    private Boolean documentoElectronicoGenerado;
}

