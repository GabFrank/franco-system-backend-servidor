package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class ConfirmarFacturaImportItemInput {
    /** Texto original del item en la factura (para aprendizaje alias). */
    private String textoOcr;
    /** Codigo original del item en la factura (para aprendizaje alias). */
    private String codigoOcr;
    /** Producto interno seleccionado por el usuario. null si se omite el item. */
    private Long productoId;
    /** Presentacion del producto seleccionada. null si se omite. */
    private Long presentacionId;
    /** Cantidad confirmada. */
    private Double cantidad;
    /** Precio unitario confirmado. */
    private Double precioUnitario;
    /** Descuento aplicado al item. */
    private Double descuento;
    /** Si true, ignora este item al crear el pedido. */
    private Boolean omitir;
}
