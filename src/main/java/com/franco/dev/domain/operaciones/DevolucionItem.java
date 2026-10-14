package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
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
@Table(name = "devolucion_item", schema = "operaciones")
public class DevolucionItem implements Identifiable<Long> {

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
    @JoinColumn(name = "devolucion_id", nullable = false)
    private Devolucion devolucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recepcion_mercaderia_item_id")
    private RecepcionMercaderiaItem recepcionMercaderiaItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Presentacion para convertir cantidad a unidad base (cantidad * presentacion.cantidad). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id")
    private Presentacion presentacion;

    /** Motivo de averia del catalogo (reason code). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_averia_id")
    private MotivoAveria motivoAveria;

    @Column(name = "cantidad", nullable = false)
    private Double cantidad;

    @Column(name = "lote")
    private String lote;

    /** Observacion libre opcional (el motivo estructurado va en motivoAveria). */
    @Column(name = "motivo")
    private String motivo;

    /** Vencimiento del producto que sale. */
    @Column(name = "vencimiento")
    private LocalDate vencimiento;

    /** Snapshot del costo unitario para valorizar la merma/gasto. */
    @Column(name = "costo_unitario")
    private Double costoUnitario;

    /** Cantidad reingresada al stock por canje directo (CON_PROVEEDOR / CANJE). */
    @Column(name = "cantidad_reingresada")
    private Double cantidadReingresada;

    /** Nuevo vencimiento del producto reingresado por canje. */
    @Column(name = "vencimiento_reingreso")
    private LocalDate vencimientoReingreso;

    /**
     * Lote del maestro del que sale la mercaderia, para productos con control de lote.
     * Opcional: sin el, la baja al separar se reparte por FEFO. El campo {@code lote}
     * (texto) guarda su numero para pantallas y etiquetas.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private Lote loteMaestro;

    /** Numero del lote que entrega el proveedor en un canje; el reingreso entra a ese lote. */
    @Column(name = "lote_reingreso")
    private String loteReingreso;

    /**
     * Movimiento de stock de la baja al separar. Solo se guarda para productos con control de
     * lote: revertir o cancelar espeja su desglose por lote. Se limpia al revertir.
     */
    @Column(name = "movimiento_baja_id")
    private Long movimientoBajaId;

    /** Movimiento de stock del reingreso del canje, para revertirlo sobre el mismo lote. */
    @Column(name = "movimiento_canje_id")
    private Long movimientoCanjeId;
} 