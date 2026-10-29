package com.franco.dev.graphql.operaciones.compras;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Un aviso sobre la factura. {@code nivel}: INFO, ADVERTENCIA o BLOQUEO (no se puede confirmar).
 * {@code tipo}: IVA, PROVEEDOR, TOTAL, DUPLICADA, MONEDA, PRODUCTOS, CANCELADA, DISTRIBUCION.
 */
@Data
@AllArgsConstructor
public class AvisoFacturaMobile {
    private String tipo;
    private String nivel;
    private String mensaje;
}
