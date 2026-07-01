package com.franco.dev.graphql.operaciones.dto;

import com.franco.dev.domain.grafico.DesgloseAnhoGrafico;
import com.franco.dev.domain.grafico.DesglosePeriodoGrafico;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoVendidoEstadistica {
    private Long productoId;
    private String descripcion;
    private Double cantidad;
    private Double totalMonto;
    /** Dinero vendido / unidades vendidas (unidad base) */
    private Double precioPromedio;
    private Double porcentaje;
    /** Entradas por COMPRA + TRANSFERENCIA (movimiento_stock, cantidad > 0) */
    private Double cantidadEntrada;
    /** Ventas según movimiento_stock tipo VENTA */
    private Double cantidadVentaMovimiento;
    /** ventaMovimiento / cantidadEntrada cuando hay entradas en el período */
    private Double indiceRotacion;
    private List<DesglosePeriodoGrafico> desglosePeriodos = new ArrayList<>();
    private List<DesgloseAnhoGrafico> desgloseAnhos = new ArrayList<>();
}
