package com.franco.dev.graphql.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveFacturaLegalToFilialResponse {
    private Long facturaId;
    private Integer numeroFactura;
    private String cdc;
    private String urlQr;
    private String estadoDocumentoElectronico;
    private String mensajeRespuestaSifen;
    private Boolean documentoElectronicoGenerado;
}

