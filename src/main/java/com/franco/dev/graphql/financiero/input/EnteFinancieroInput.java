package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.SituacionPagoEnte;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EnteFinancieroInput {
    private Long id;
    private Long enteId;
    private SituacionPagoEnte situacionPago;
    private Long proveedorId;
    private Long monedaId;
    private BigDecimal montoTotal;
    private BigDecimal montoYaPagado;
    private Integer cantidadCuotas;
    private Integer diaVencimiento;
    private Long usuarioId;
}
