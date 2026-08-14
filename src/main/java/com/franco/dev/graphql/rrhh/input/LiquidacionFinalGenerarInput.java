package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.MotivoEgreso;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Parámetros configurables del finiquito antes de generar el borrador. Los campos
 * null se auto-calculan; los toggles null se tratan como true (cobrar/descontar).
 */
@Data
public class LiquidacionFinalGenerarInput {
    private Long funcionarioId;
    private MotivoEgreso motivoEgreso;
    private String fechaEgreso;
    private Long monedaId;
    private String fechaIngreso;
    private BigDecimal salarioBase;
    private Integer diasTrabajadosMes;
    private Boolean preavisoOtorgado;
    private Integer preavisoDias;
    private Integer diasVacaciones;
    private BigDecimal aguinaldo;
    private BigDecimal ipsBase;
    private Boolean descontarIps;
    private Boolean cobrarVales;
    private Boolean cobrarConvenios;
    private Boolean cobrarPrestamos;
    private Boolean descontarPenalizaciones;
}
