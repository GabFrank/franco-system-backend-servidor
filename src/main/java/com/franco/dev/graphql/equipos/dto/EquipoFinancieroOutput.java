package com.franco.dev.graphql.equipos.dto;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EquipoFinancieroOutput {
    private Long id;
    private BigDecimal costo;
    private BigDecimal valorTasacion;
    private BigDecimal valorTasacionPyg;
    private BigDecimal valorTasacionBrl;
    private String situacionPago;
    private Proveedor proveedor;
    private Moneda moneda;
    private BigDecimal montoTotal;
    private BigDecimal montoYaPagado;
    private Integer cantidadCuotas;
    private Integer cantidadCuotasPagadas;
    private Integer diaVencimiento;
    private Usuario usuario;
    private LocalDateTime creadoEn;
}
