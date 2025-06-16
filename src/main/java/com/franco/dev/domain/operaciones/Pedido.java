package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDef(
        name = "pedido_estado",
        typeClass = PostgreSQLEnumType.class
)
@TypeDef(
        name = "pedido_forma_pago",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "pedido", schema = "operaciones")
public class Pedido implements Identifiable<Long> {

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
    @JoinColumn(name = "necesidad_id", nullable = true)
    private Necesidad necesidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = true)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = true)
    private Vendedor vendedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pago_id", nullable = true)
    private FormaPago formaPago;

    private String tipoBoleta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    @Column(name = "plazo_credito")
    private Integer plazoCredito;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "pedido_estado")
    private PedidoEstado estado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    // Step tracking fields following the same pattern as PedidoItem

    // Step: Creacion (Datos del pedido)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_creacion_id", nullable = true)
    private Usuario usuarioCreacion;

    @Column(name = "fecha_inicio_creacion")
    private LocalDateTime fechaInicioCreacion;

    @Column(name = "fecha_fin_creacion")
    private LocalDateTime fechaFinCreacion;

    @Column(name = "progreso_creacion")
    private Integer progresoCreacion = 0;

    // Step: Recepcion Nota
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_recepcion_nota_id", nullable = true)
    private Usuario usuarioRecepcionNota;

    @Column(name = "fecha_inicio_recepcion_nota")
    private LocalDateTime fechaInicioRecepcionNota;

    @Column(name = "fecha_fin_recepcion_nota")
    private LocalDateTime fechaFinRecepcionNota;

    @Column(name = "progreso_recepcion_nota")
    private Integer progresoRecepcionNota = 0;

    // Step: Recepcion Mercaderia
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_recepcion_mercaderia_id", nullable = true)
    private Usuario usuarioRecepcionMercaderia;

    @Column(name = "fecha_inicio_recepcion_mercaderia")
    private LocalDateTime fechaInicioRecepcionMercaderia;

    @Column(name = "fecha_fin_recepcion_mercaderia")
    private LocalDateTime fechaFinRecepcionMercaderia;

    @Column(name = "progreso_recepcion_mercaderia")
    private Integer progresoRecepcionMercaderia = 0;

    // Step: Solicitud Pago
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_solicitud_pago_id", nullable = true)
    private Usuario usuarioSolicitudPago;

    @Column(name = "fecha_inicio_solicitud_pago")
    private LocalDateTime fechaInicioSolicitudPago;

    @Column(name = "fecha_fin_solicitud_pago")
    private LocalDateTime fechaFinSolicitudPago;

    @Column(name = "progreso_solicitud_pago")
    private Integer progresoSolicitudPago = 0;
}