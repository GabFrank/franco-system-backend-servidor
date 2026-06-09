package com.franco.dev.graphql.activos.input;

import com.franco.dev.graphql.financiero.input.CuotaDetalleInput;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class InmuebleInput {
    private Long id;
    private Long propietarioId;
    private String nombreAsignado;
    private Long paisId;
    private Long ciudadId;
    private String direccion;
    private String googleMapsUrl;
    private String codigoCatastral;
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
    private Boolean esPropio;
    private Long alquilerProveedorId;
    private BigDecimal alquilerMonto;
    private Integer alquilerDiaVencimiento;
    private LocalDate alquilerVigencia;
    private Long usuarioId;
    private List<CuotaDetalleInput> cuotasDetalle;
}
