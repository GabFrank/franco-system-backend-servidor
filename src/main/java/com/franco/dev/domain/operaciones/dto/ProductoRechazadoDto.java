package com.franco.dev.domain.operaciones.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un producto rechazado, sea en una transferencia o en una compra. Es una fila de la pantalla
 * "Productos rechazados" del desktop.
 *
 * <ul>
 *   <li>{@code fuente}: TRANSFERENCIA o COMPRA.</li>
 *   <li>{@code etapa}: donde se rechazó. Transferencia: PREPARACION, TRANSPORTE o RECEPCION (la
 *       primera etapa con motivo de rechazo). Compra: RECEPCION_FISICA o RECEPCION_DOCUMENTAL.</li>
 *   <li>{@code referenciaId}: id de la transferencia o del pedido de compra, para abrirlo.</li>
 *   <li>{@code origen}/{@code destino}: sucursales de la transferencia; en compras, el proveedor
 *       y la sucursal que recibía.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class ProductoRechazadoDto {
    private String fuente;
    private String etapa;
    private Long referenciaId;
    private Long itemId;
    private LocalDateTime fecha;
    private Long productoId;
    private String productoDescripcion;
    private String presentacion;
    private Double cantidad;
    private String motivo;
    private String observacion;
    private String origen;
    private String destino;
    private String usuario;
}
