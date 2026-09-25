package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.math.BigDecimal;

/** Ítem de la nota de remisión: qué se traslada. Sin precio ni IVA (la NRE no lleva valores). */
@Data
public class NotaRemisionItemInput {

    private Long id;
    private Long productoId;
    private Long presentacionId;
    private String codigo;
    private String descripcion;
    private BigDecimal cantidad;
    private String unidadMedida;
}
