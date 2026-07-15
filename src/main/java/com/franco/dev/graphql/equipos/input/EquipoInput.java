package com.franco.dev.graphql.equipos.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EquipoInput {
    private Long id;
    private Long propietarioId;
    private String identificador;
    private Long modeloId;
    private String descripcion;
    private String imagenes;
    private Long tipoEquipoId;
    private Boolean consumeEnergia;
    private String consumoValor;
    private Long sucursalId;
    private Long usuarioId;
    private EquipoFinancieroInput financiero;

    // Campos planos conservados para compatibilidad; el servicio los mapea a financiero.
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
}
