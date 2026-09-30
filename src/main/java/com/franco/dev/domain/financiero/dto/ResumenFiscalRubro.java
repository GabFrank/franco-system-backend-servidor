package com.franco.dev.domain.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Una linea de la vista por rubros del formulario 120 de IVA. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumenFiscalRubro {
    /** "RUBRO 1". */
    private String rubro;
    /** "A", "B"... */
    private String inciso;
    private String concepto;
    private Double gravada10;
    private Double gravada5;
    private Double iva10;
    private Double iva5;
    private Double exentas;
}
