package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

/**
 * Linea confirmada de una nota de credito consolidada. cantidad/costoUnitario
 * van en la presentacion elegida; cantidadBase es el canonico en unidad base.
 */
@Data
public class NotaCreditoDevolucionItemInput {
    private Long productoId;
    private Long presentacionId;
    private Double cantidad;
    private Double costoUnitario;
    private Double cantidadBase;
    private Double total;
}
