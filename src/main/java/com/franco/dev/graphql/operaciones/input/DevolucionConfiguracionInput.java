package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

/** Input de configuracion del modulo de devoluciones. Campos null = no cambia. */
@Data
public class DevolucionConfiguracionInput {
    private Integer diasEstancada;
    private Integer diasEstancadaUrgente;
    private Integer rankingTopN;
    private String dashboardRangoDefault;
    private Integer ticketAnchoMm;
    private String impresoraTicket;
    private String comprobanteTitulo;
    private String comprobanteTextoFirma;
    private Boolean mermaRespetaMotivo;
    private String tipoGastoMerma;
    private Boolean alertaIncluyeRetirado;
    private Boolean alertaBloqueante;
}
