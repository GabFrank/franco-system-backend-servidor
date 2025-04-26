package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import javax.persistence.*;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@DynamicInsert //permite que los valores por default declarados en la base de datos sean utilizados
@TypeDef(
        name = "pedido_item_estado",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "pedido_item", schema = "operaciones")
public class PedidoItem implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(
            name = "assigned-identity",
            strategy = "com.franco.dev.config.AssignedIdentityGenerator"
    )
    @GeneratedValue(
            generator = "assigned-identity",
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = true)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = true)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_creacion_id", nullable = true)
    private Presentacion presentacionCreacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_pedido_id", nullable = true)
    private NotaPedido notaPedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_recepcion_id", nullable = true)
    private NotaRecepcion notaRecepcion;

    @Column(name = "precio_unitario_creacion")
    private Double precioUnitarioCreacion;

    @Column(name = "descuento_unitario_creacion")
    private Double descuentoUnitarioCreacion;

    private Boolean bonificacion = false;

    @Column(name = "bonificacion_detalle")
    private String bonificacionDetalle;

    private String observacion;

    private Boolean frio = false;

    private Double cantidadCreacion;

    private LocalDateTime vencimientoCreacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "pedido_item_estado")
    private PedidoItemEstado estado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_creacion_id", nullable = true)
    private Usuario usuarioCreacion;

    private Double precioUnitarioRecepcionNota;

    private Double descuentoUnitarioRecepcionNota;

    private LocalDateTime vencimientoRecepcionNota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_recepcion_nota_id", nullable = true)
    private Presentacion presentacionRecepcionNota;

    private Double cantidadRecepcionNota;

    private Double precioUnitarioRecepcionProducto;

    private Double descuentoUnitarioRecepcionProducto;

    private LocalDateTime vencimientoRecepcionProducto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_recepcion_producto_id", nullable = true)
    private Presentacion presentacionRecepcionProducto;

    private Double cantidadRecepcionProducto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_recepcion_nota_id", nullable = true)
    private Usuario usuarioRecepcionNota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_recepcion_producto_id", nullable = true)
    private Usuario usuarioRecepcionProducto;
    private String obsCreacion;
    private String obsRecepcionNota;
    private String obsRecepcionProducto;
    private Boolean autorizacionRecepcionNota;
    private Boolean autorizacionRecepcionProducto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_recepcion_nota_id", nullable = true)
    private Usuario autorizadoPorRecepcionNota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_recepcion_producto_id", nullable = true)
    private Usuario autorizadoPorRecepcionProducto;
    private String motivoModificacionRecepcionNota;
    private String motivoModificacionRecepcionProducto;
    private String motivoRechazoRecepcionNota;
    private String motivoRechazoRecepcionProducto;
    private Boolean cancelado = false;
    private Boolean verificadoRecepcionNota = false;
    private Boolean verificadoRecepcionProducto = false;
}



