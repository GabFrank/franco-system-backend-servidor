package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/**
 * Cuánto de un renglón de la factura va a una sucursal, en la unidad de la factura (lo que dice el papel: 5 de
 * las 14 cajas). El central lo pasa a unidades base con el factor del renglón.
 */
@Data
public class RepartoRenglonMobileInput {
    private Long renglonId;
    private Long sucursalId;
    private Double cantidad;
}
