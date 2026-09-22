package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class ConfiguracionVentaTarjetaInput {
    private Long id;
    private Boolean habilitado;
    /** LIBRE | AVISA_AL_CERRAR | BLOQUEA_EL_CIERRE */
    private String registroObligatorio;
    private java.math.BigDecimal toleranciaDiferenciaMontoPct;
    private Integer minutosValidezCaptura;
    private Integer segundosDialogoRegistro;
    private Integer horasVentanaDuplicado;
    private Integer diasRetencionImagenes;
    private Integer mbLibresMinimos;
    private Long usuarioId;
}
