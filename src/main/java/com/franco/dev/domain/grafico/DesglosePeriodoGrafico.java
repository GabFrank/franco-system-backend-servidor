package com.franco.dev.domain.grafico;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DesglosePeriodoGrafico {
    private String etiqueta;
    private Double total;
    private Double cantidad;
}
