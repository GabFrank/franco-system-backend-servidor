package com.franco.dev.domain.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumenFacturasDto {
    private Long cantFacturas;
    private Integer maxNumero;
    private Integer minNumero;
    private Double totalFinal;
    private Double total5;
    private Double total10;
    private Double total0;
}
