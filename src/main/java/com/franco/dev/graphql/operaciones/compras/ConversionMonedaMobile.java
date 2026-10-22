package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/**
 * Cómo quedan los precios del pedido si se elige otra moneda que la de la factura. La nota sigue en la moneda de
 * la factura (lo que informa la SET); el pedido se convierte con la cotización.
 */
@Data
public class ConversionMonedaMobile {
    private Long monedaId;
    private String monedaSimbolo;
    private Boolean mismaMoneda;
    /** Multiplica el precio de la factura para llevarlo a la moneda del pedido. */
    private Double factor;
    private Double cotizacionFactura;
    private Double cotizacionPedido;
    private Double totalPedido;
    /** Listo para mostrar debajo del campo. */
    private String texto;
}
