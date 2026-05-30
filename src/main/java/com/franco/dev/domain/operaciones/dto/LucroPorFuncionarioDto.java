package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LucroPorFuncionarioDto {
    private Long usuarioId;
    private String nombreFuncionario;
    private Double costoTotal;
    private Double cantidad;
    private Double totalVenta;
    private Double lucro;
    private Double percent;
    private Double ventaMedia;
    private Double margen;
    private Double costoUnitario;
    private Double totalDescuento;
    private Double totalAumento;

    public LucroPorFuncionarioDto(
            Long usuarioId, String nombreFuncionario, Double costoTotal, Double cantidad,
            Double totalVenta, Double lucro, Double percent, Double ventaMedia, Double margen,
            Double totalDescuento, Double totalAumento) {
        this.usuarioId = usuarioId;
        this.nombreFuncionario = nombreFuncionario;
        this.costoTotal = costoTotal;
        this.cantidad = cantidad;
        this.totalVenta = totalVenta;
        this.lucro = lucro;
        this.percent = percent;
        this.ventaMedia = ventaMedia;
        this.margen = margen;
        this.costoUnitario = null;
        this.totalDescuento = totalDescuento;
        this.totalAumento = totalAumento;
    }

    public void aggregate(LucroPorFuncionarioDto other) {
        this.cantidad += other.cantidad;
        this.totalVenta += other.totalVenta;
        this.costoTotal += other.costoTotal;
        this.lucro += other.lucro;
        this.totalDescuento += other.totalDescuento != null ? other.totalDescuento : 0.0;
        this.totalAumento += other.totalAumento != null ? other.totalAumento : 0.0;
    }
}
