package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuanto se vendio de un producto en una sucursal, en un rango de fechas.
 *
 * Proyeccion de una sola consulta agrupada. Existe para que el cliente no tenga que bajarse los
 * movimientos de venta —hasta 1000 por sucursal— solo para sumarles la cantidad.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VentasPorSucursalDto {
    private Long sucursalId;
    private Double totalVentas;
}
