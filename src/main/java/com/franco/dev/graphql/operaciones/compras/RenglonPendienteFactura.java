package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/**
 * Un renglón de la factura que quedó afuera de la compra (#124): pendiente de cargar como ítem o descartado con su
 * motivo. Lo muestra el desktop en la planificación.
 */
@Data
public class RenglonPendienteFactura {
    private Long id;
    private Integer orden;
    private String descripcion;
    private Double cantidad;
    private String unidadMedida;
    private Double precioUnitario;
    private Double total;
    /** yyyy-MM-dd */
    private String vencimiento;
    /**
     * Precio neto por unidad de la factura llevado a la moneda del pedido con la cotización del pedido. Null si las
     * monedas difieren y el pedido no tiene cotización.
     */
    private Double precioPedido;
    private Boolean descartado;
    private String motivoDescarte;
    /** Nombre de quien lo descartó. */
    private String descartadoPor;
}
