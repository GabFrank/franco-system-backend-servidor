package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Presentacion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "recepcion_mercaderia_item", schema = "operaciones")
public class RecepcionMercaderiaItem implements Identifiable<Long> {

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
    @JoinColumn(name = "recepcion_mercaderia_id", nullable = false)
    private RecepcionMercaderia recepcionMercaderia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_recepcion_item_id", nullable = false)
    private NotaRecepcionItem notaRecepcionItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_item_distribucion_id")
    private PedidoItemDistribucion pedidoItemDistribucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_recibida_id")
    private Presentacion presentacionRecibida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_entrega_id", nullable = false)
    private Sucursal sucursalEntrega;

    @Column(name = "cantidad_recibida", nullable = false)
    private Double cantidadRecibida;

    @Column(name = "cantidad_rechazada")
    private Double cantidadRechazada;

    @Column(name = "es_bonificacion")
    private Boolean esBonificacion = false;

    @Column(name = "vencimiento_recibido")
    private LocalDate vencimientoRecibido;

    @Column(name = "lote")
    private String lote;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "observaciones")
    private String observaciones;
} 