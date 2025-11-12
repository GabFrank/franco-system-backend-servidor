package com.franco.dev.graphql.financiero.input;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DocumentoElectronicoInput {
  private String cdc;
  private String estadoSifen;
  private String codigoRespuestaSifen;
  private String mensajeRespuestaSifen;
  private String xmlFirmado;
  private String urlQr;
  private Long usuarioId;
  private LocalDateTime creadoEn;
  private LocalDateTime actualizadoEn;
  private String numeroDocumento;
  private String tipoDocumento;
  private LocalDateTime fechaEmision;
  private LocalDateTime fechaRecepcionSifen;
  private Long loteDeId;
  private Long facturaLegalId;
  private Long sucursalId;
}
