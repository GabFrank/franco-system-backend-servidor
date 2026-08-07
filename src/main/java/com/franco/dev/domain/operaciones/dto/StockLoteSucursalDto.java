package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Saldo de un lote en una sucursal puntual. Es el desglose que abre la pantalla "Stock por lotes"
 * al expandir una fila, ahora que la fila es el lote y no el par lote/sucursal.
 *
 * Incluye las sucursales activas sin movimientos, con cantidad 0: la pregunta que responde la
 * pantalla es "dónde está este lote", y para eso importa tanto dónde hay como dónde no.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockLoteSucursalDto {
    private Long sucursalId;
    private String sucursalNombre;
    private Double cantidadDisponible;
}
