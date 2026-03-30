package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LucroPorProductosDto {
    private Long productoId;
    private String descripcion;
    private Double costoTotal; // ← Nuevo campo
    private Double cantidad;
    private Double totalVenta;
    private Double lucro;
    private Double percent;
    private Double ventaMedia;
    private Double margen;
    private Double costoUnitario; // ← Calculado después
    private Double totalDescuento;
    private Double totalAumento;

    // Constructor que coincida con la consulta SQL (sin costoUnitario)
    public LucroPorProductosDto(
            Long productoId, String descripcion, Double costoTotal, Double cantidad,
            Double totalVenta, Double lucro, Double percent, Double ventaMedia, Double margen, Double totalDescuento,
            Double totalAumento) {
        this.productoId = productoId;
        this.descripcion = descripcion;
        this.costoTotal = costoTotal;
        this.cantidad = cantidad;
        this.totalVenta = totalVenta;
        this.lucro = lucro;
        this.percent = percent;
        this.ventaMedia = ventaMedia;
        this.margen = margen;
        this.costoUnitario = null; // Se calculará después
        this.totalDescuento = totalDescuento;
        this.totalAumento = totalAumento;
    }

    public void aggregate(LucroPorProductosDto other) {
        this.cantidad += other.cantidad;
        this.totalVenta += other.totalVenta;
        this.costoTotal += other.costoTotal; // Sumar costos totales
        this.lucro += other.lucro;
        this.totalDescuento += other.totalDescuento != null ? other.totalDescuento : 0.0;
        this.totalAumento += other.totalAumento != null ? other.totalAumento : 0.0;
    }
}
