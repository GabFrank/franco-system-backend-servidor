package com.franco.dev.graphql.activos.input;

import com.franco.dev.graphql.financiero.input.CuotaDetalleInput;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MuebleInput {
    private Long id;
    private Long propietarioId;
    private String identificador;
    private String descripcion;
    private Long familiaId;
    private Long tipoMuebleId;
    private Boolean consumeEnergia;
    private String consumoValor;
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
