package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.math.BigDecimal;

/** Semantica PATCH: un campo null no pisa lo guardado. El usuario sale de la sesion, no del input. */
@Data
public class ConfiguracionCobroInput {
    private Boolean aumentoConTope;
    private BigDecimal aumentoTopePorcentaje;
}
