package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Factura electrónica de un proveedor tal como la devolvió SIFEN (siConsDE, código 0422).
 *
 * Hace de caché: un CDC se consulta una sola vez. Cuando el comprador la confirma en el celular apunta a la
 * nota de recepción que generó; la nota no sabe nada de esto.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "documento_recibido", schema = "operaciones")
public class DocumentoRecibido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cdc;
    @Column(name = "tipo_documento")
    private Integer tipoDocumento;
    @Column(name = "ruc_emisor")
    private String rucEmisor;
    @Column(name = "dv_emisor")
    private String dvEmisor;
    @Column(name = "razon_social_emisor")
    private String razonSocialEmisor;
    @Column(name = "ruc_receptor")
    private String rucReceptor;
    @Column(name = "dv_receptor")
    private String dvReceptor;
    @Column(name = "razon_social_receptor")
    private String razonSocialReceptor;
    private Integer timbrado;
    private String establecimiento;
    @Column(name = "punto_expedicion")
    private String puntoExpedicion;
    private String numero;
    @Column(name = "fecha_emision")
    private LocalDateTime fechaEmision;
    @Column(name = "condicion_operacion")
    private Integer condicionOperacion;
    private String moneda;
    private Double cotizacion;
    @Column(name = "subtotal_exenta")
    private Double subtotalExenta;
    @Column(name = "subtotal_iva5")
    private Double subtotalIva5;
    @Column(name = "subtotal_iva10")
    private Double subtotalIva10;
    @Column(name = "total_descuento")
    private Double totalDescuento;
    @Column(name = "total_general")
    private Double totalGeneral;
    @Column(name = "total_iva5")
    private Double totalIva5;
    @Column(name = "total_iva10")
    private Double totalIva10;
    @Column(name = "total_iva")
    private Double totalIva;
    @Column(name = "codigo_respuesta")
    private String codigoRespuesta;
    @Column(name = "mensaje_respuesta")
    private String mensajeRespuesta;
    private String xml;
    @Column(name = "fecha_consulta")
    private LocalDateTime fechaConsulta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_recepcion_id")
    private NotaRecepcion notaRecepcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
