package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PedidoInput {
    private Long id;
    private Long vendedorId;
    private Long proveedorId;
    private LocalDateTime fechaDeEntrega;
    private Long formaPagoId;
    private String tipoBoleta;
    private PedidoEstado estado;
    private Long monedaId;
    private Integer plazoCredito;
    private Float descuento;
    private Float valorTotal;
    private Long usuarioId;
    private LocalDateTime creadoEn;
    private List<PedidoItemInput> pedidoItemInputList;
    
    // Step tracking fields following the same pattern as PedidoItem
    
    // Step: Creacion (Datos del pedido)
    private Long usuarioCreacionId;
    private LocalDateTime fechaInicioCreacion;
    private LocalDateTime fechaFinCreacion;
    private Integer progresoCreacion;
    
    // Step: Recepcion Nota
    private Long usuarioRecepcionNotaId;
    private LocalDateTime fechaInicioRecepcionNota;
    private LocalDateTime fechaFinRecepcionNota;
    private Integer progresoRecepcionNota;
    
    // Step: Recepcion Mercaderia
    private Long usuarioRecepcionMercaderiaId;
    private LocalDateTime fechaInicioRecepcionMercaderia;
    private LocalDateTime fechaFinRecepcionMercaderia;
    private Integer progresoRecepcionMercaderia;
    
    // Step: Solicitud Pago
    private Long usuarioSolicitudPagoId;
    private LocalDateTime fechaInicioSolicitudPago;
    private LocalDateTime fechaFinSolicitudPago;
    private Integer progresoSolicitudPago;
}


