package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Valores por defecto (auto-calculados) del finiquito, para precargar el diálogo
 * de generación sin persistir nada. Mapea al type GraphQL LiquidacionFinalPreview.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiquidacionFinalPreview {
    private String fechaIngreso;
    private Integer antiguedadAnios;
    private Integer antiguedadDias;
    private BigDecimal salarioPromedio;
    private BigDecimal sueldoBase;
    private Integer diasTrabajadosMes;
    private BigDecimal salarioDelMes;
    private BigDecimal aguinaldoProporcional;
    private Integer diasVacacionesNoGozadas;
    private Integer preavisoDias;
    private BigDecimal ipsBase;
    private Boolean ipsActivo;
}
