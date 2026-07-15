package com.franco.dev.graphql.equipos.input;

import com.franco.dev.graphql.financiero.input.CuotaDetalleInput;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class EquipoFinancieroInput {
    private Long id;
    private BigDecimal costo;
    private BigDecimal valorTasacion;
    private BigDecimal valorTasacionPyg;
    private BigDecimal valorTasacionBrl;
    private String situacionPago;
    private Long proveedorId;
    private Long monedaId;
    private BigDecimal montoTotal;
    private BigDecimal montoYaPagado;
    private Integer cantidadCuotas;
    private Integer cantidadCuotasPagadas;
    private Integer diaVencimiento;
    private Long usuarioId;
    private List<CuotaDetalleInput> cuotasDetalle;
}
