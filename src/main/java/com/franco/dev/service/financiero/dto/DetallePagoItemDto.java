package com.franco.dev.service.financiero.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Una linea del detalle de un evento de pago: que documento se pago y cuanto se le imputo.
 *
 * <p>Existe porque el evento postea <b>un</b> movimiento consolidado en la caja: la descripcion
 * de ese movimiento no puede nombrar a los N documentos, asi que el desglose se consulta aparte
 * (ver {@code PagoProveedorService.etiquetaDe}).</p>
 */
@Data
public class DetallePagoItemDto {
    /** Id de la obligacion de pago (SolicitudPago). */
    private Long solicitudPagoId;
    /** COMPRA | GASTO | RRHH. */
    private String tipo;
    /** Descripcion del documento: la observacion de la solicitud. */
    private String descripcion;
    private String proveedorNombre;
    private String monedaDenominacion;
    private String monedaSimbolo;
    private Integer decimales;
    /** Monto imputado a este documento en este evento (suma de sus lineas). */
    private BigDecimal montoImputado;
    /** Total del documento, para ver si quedo saldado. */
    private BigDecimal montoTotal;
    private BigDecimal montoPagado;
    private String estado;
}
