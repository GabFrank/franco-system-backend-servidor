package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Los insumos de la cantidad sugerida de un producto en una sucursal, en un rango de fechas.
 *
 * Junta lo que devuelven {@link VentasPorSucursalDto} y {@link ComprasPorSucursalDto}. Son los
 * cuatro numeros que consume el diálogo de ítem de compra y nada mas: la cuenta final se hace en
 * el cliente, que es el unico que conoce el stock actual contra el que se resta.
 *
 * Una sucursal sin movimientos en el rango no aparece —no hay filas que agrupar—, igual que en
 * {@link StockPorSucursalDto}. El llamador la muestra en cero.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CantidadSugeridaPorSucursalDto {
    private Long sucursalId;
    private Double totalVentas;
    private Long cantidadCompras;
    private LocalDateTime primeraCompra;
    private LocalDateTime ultimaCompra;
}
