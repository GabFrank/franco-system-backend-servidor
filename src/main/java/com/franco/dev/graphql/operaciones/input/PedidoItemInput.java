package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import lombok.Data;

import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

@Data
public class PedidoItemInput {
    private Long id;
    private Long productoId;
    private Long pedidoId;
    private Double precioUnitarioCreacion;
    private Double descuentoUnitarioCreacion;
    private Double valorTotal;
    private Boolean bonificacion;
    private String bonificacionDetalle;
    private String observacion;
    private String vencimientoCreacion;
    private Boolean frio;
    private Long usuarioCreacionId;
    private PedidoItemEstado estado;
    private Long presentacionCreacionId;
    private Double cantidadCreacion;
    private String creadoEn;
    private Long notaPedidoId;
    private Long notaRecepcionId;
    private Double precioUnitarioRecepcionNota;
    private Double descuentoUnitarioRecepcionNota;
    private String vencimientoRecepcionNota;
    private Long presentacionRecepcionNotaId;
    private Double cantidadRecepcionNota;
    private Double precioUnitarioRecepcionProducto;
    private Double descuentoUnitarioRecepcionProducto;
    private String vencimientoRecepcionProducto;
    private Long presentacionRecepcionProductoId;
    private Double cantidadRecepcionProducto;
    private Long usuarioRecepcionNotaId;
    private Long usuarioRecepcionProductoId;
    private String obsCreacion;
    private String obsRecepcionNota;
    private String obsRecepcionProducto;
    private Boolean autorizacionRecepcionNota;
    private Boolean autorizacionRecepcionProducto;
    private Long autorizadoPorRecepcionNotaId;
    private Long autorizadoPorRecepcionProductoId;
    private String motivoModificacionRecepcionNota;
    private String motivoModificacionRecepcionProducto;
    private Boolean cancelado;
    private Boolean verificadoRecepcionNota;
    private Boolean verificadoRecepcionProducto;
    private String motivoRechazoRecepcionNota;
    private String motivoRechazoRecepcionProducto;
}
