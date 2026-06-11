package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoVendidoEstadistica {
    private Long productoId;
    private String descripcion;
    private Double cantidad;
    private Double totalMonto;
    private Double porcentaje;
    /** Entradas por COMPRA + TRANSFERENCIA (movimiento_stock, cantidad > 0) */
    private Double cantidadEntrada;
    /** Ventas según movimiento_stock tipo VENTA */
    private Double cantidadVentaMovimiento;
    /** ventaMovimiento / cantidadEntrada cuando hay entradas en el período */
    private Double indiceRotacion;
}
