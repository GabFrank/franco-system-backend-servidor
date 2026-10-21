package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;

/** Un renglón de la factura del proveedor y el resultado de cruzarlo con el pedido. */
@Data
@NoArgsConstructor
@Entity
@Table(name = "documento_recibido_item", schema = "operaciones")
public class DocumentoRecibidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_recibido_id", nullable = false)
    private DocumentoRecibido documentoRecibido;

    private Integer orden;
    @Column(name = "codigo_proveedor")
    private String codigoProveedor;
    private String gtin;
    @Column(name = "gtin_paquete")
    private String gtinPaquete;
    private String descripcion;
    @Column(name = "unidad_medida")
    private String unidadMedida;
    private Double cantidad;
    /** dPUniProSer: precio por unidad facturada, IVA incluido (así factura SIFEN). */
    @Column(name = "precio_unitario")
    private Double precioUnitario;
    /** dDescItem: descuento por unidad facturada. */
    @Column(name = "descuento_unitario")
    private Double descuentoUnitario;
    @Column(name = "porcentaje_descuento")
    private Double porcentajeDescuento;
    /** dTotOpeItem: total del renglón, IVA incluido y con el descuento aplicado. */
    private Double total;
    @Column(name = "afectacion_iva")
    private Integer afectacionIva;
    @Column(name = "tasa_iva")
    private Integer tasaIva;
    private String lote;
    private LocalDate vencimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id")
    private Presentacion presentacion;

    /** Unidades base por cada unidad facturada. */
    private Double factor;

    /** Cómo se reconoció el producto: ver {@code TipoMatchRenglon}. */
    @Column(name = "tipo_match")
    private String tipoMatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_item_id")
    private PedidoItem pedidoItem;

    @Column(name = "es_bonificacion")
    private Boolean esBonificacion = false;

    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_recepcion_item_id")
    private NotaRecepcionItem notaRecepcionItem;
}
