package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionItemEstado;
import com.franco.dev.domain.productos.Presentacion;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class NotaRecepcionItemInput {
    private Long id;
    private Long notaRecepcionId;
    private Long pedidoItemId;
    private Long productoId;
    private Long presentacionEnNotaId;
    private Double cantidadEnNota;
    private Double precioUnitarioEnNota;
    private Boolean esBonificacion;
    private LocalDate vencimientoEnNota;
    private String observacion;
    private NotaRecepcionItemEstado estado;
    private String motivoRechazo;
    private LocalDateTime creadoEn;
    private Long usuarioId;

    /** Los mismos campos que manda el desktop al volver a guardar un ítem existente (`NotaRecepcionItem.toInput`). */
    public static NotaRecepcionItemInput copiaDe(NotaRecepcionItem item) {
        NotaRecepcionItemInput input = new NotaRecepcionItemInput();
        input.setId(item.getId());
        input.setNotaRecepcionId(item.getNotaRecepcion() != null ? item.getNotaRecepcion().getId() : null);
        input.setPedidoItemId(item.getPedidoItem() != null ? item.getPedidoItem().getId() : null);
        input.setProductoId(item.getProducto() != null ? item.getProducto().getId() : null);
        input.setPresentacionEnNotaId(item.getPresentacionEnNota() != null ? item.getPresentacionEnNota().getId() : null);
        input.setCantidadEnNota(item.getCantidadEnNota());
        input.setPrecioUnitarioEnNota(item.getPrecioUnitarioEnNota());
        input.setEsBonificacion(item.getEsBonificacion());
        input.setVencimientoEnNota(item.getVencimientoEnNota());
        input.setObservacion(item.getObservacion());
        input.setEstado(item.getEstado());
        input.setMotivoRechazo(item.getMotivoRechazo());
        return input;
    }

    /** Ítem nuevo (sin nota todavía) con los datos del ítem del pedido: precio pedido, vencimiento esperado, sin bonificación. */
    public static NotaRecepcionItemInput desdePedidoItem(PedidoItem pedidoItem, Presentacion presentacion, double cantidadBase,
                                                         NotaRecepcionItemEstado estado, String observacion,
                                                         String motivoRechazo) {
        NotaRecepcionItemInput input = new NotaRecepcionItemInput();
        input.setPedidoItemId(pedidoItem.getId());
        input.setProductoId(pedidoItem.getProducto() != null ? pedidoItem.getProducto().getId() : null);
        input.setPresentacionEnNotaId(presentacion != null ? presentacion.getId() : null);
        input.setCantidadEnNota(cantidadBase);
        input.setPrecioUnitarioEnNota(pedidoItem.getPrecioUnitarioSolicitado() != null
                ? pedidoItem.getPrecioUnitarioSolicitado() : 0.0);
        input.setEsBonificacion(false);
        input.setVencimientoEnNota(pedidoItem.getVencimientoEsperado() != null
                ? pedidoItem.getVencimientoEsperado().toLocalDate() : null);
        input.setObservacion(observacion);
        input.setEstado(estado);
        input.setMotivoRechazo(motivoRechazo);
        return input;
    }
}
