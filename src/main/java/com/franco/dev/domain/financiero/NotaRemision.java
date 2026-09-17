package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.enums.ModalidadTransporteNr;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaRemision;
import com.franco.dev.domain.financiero.enums.OrigenNotaRemision;
import com.franco.dev.domain.financiero.enums.ResponsableEmisionNr;
import com.franco.dev.domain.financiero.enums.TipoTransporteNr;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Nota de Remisión Electrónica (NRE, iTiDE 7): ampara el traslado de mercadería.
 *
 * Central-only (no se replica a las filiales), PK compuesta {@code (id, sucursal_id)} como
 * factura_legal. Los datos de vehículo, chofer y transportista se guardan como **snapshot** además
 * de la FK al catálogo: el catálogo puede cambiar después y lo que viaja al XML y al KuDE es lo que
 * valía el día del traslado.
 *
 * Las FK viajan como columnas planas (no relaciones): las relaciones compuestas de este repo se
 * mapean {@code insertable = false} y entonces no se persisten (ver DocumentoElectronico y §13.1
 * del plan). Los resolvers resuelven cada objeto por su id.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "nota_remision", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class NotaRemision implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "timbrado_detalle_id", nullable = false)
    private Long timbradoDetalleId;

    /** Serie propia de la nota de remisión dentro del timbrado, independiente de la de facturas. */
    @Column(name = "numero_nota_remision", nullable = false)
    private Integer numeroNotaRemision;

    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 20)
    private OrigenNotaRemision origen;

    @Column(name = "transferencia_id")
    private Long transferenciaId;

    @Column(name = "factura_legal_id")
    private Long facturaLegalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_emision", nullable = false, length = 50)
    private MotivoEmisionNotaRemision motivoEmision;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsable_emision", nullable = false, length = 50)
    private ResponsableEmisionNr responsableEmision;

    @Column(name = "km_estimado")
    private Integer kmEstimado;

    @Column(name = "fecha_inicio_traslado")
    private LocalDate fechaInicioTraslado;

    @Column(name = "fecha_fin_traslado")
    private LocalDate fechaFinTraslado;

    /** Solo cuando el motivo es traslado por ventas y todavía no hay factura. */
    @Column(name = "fecha_estimada_factura")
    private LocalDate fechaEstimadaFactura;

    // --- receptor (SIFEN no admite innominado en una NRE) ---
    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "receptor_nombre", nullable = false)
    private String receptorNombre;

    @Column(name = "receptor_ruc")
    private String receptorRuc;

    @Column(name = "receptor_direccion")
    private String receptorDireccion;

    @Column(name = "receptor_departamento")
    private String receptorDepartamento;

    @Column(name = "receptor_codigo_ciudad")
    private Integer receptorCodigoCiudad;

    @Column(name = "receptor_ciudad")
    private String receptorCiudad;

    // --- salida y entrega ---
    @Column(name = "salida_direccion")
    private String salidaDireccion;

    @Column(name = "salida_departamento")
    private String salidaDepartamento;

    @Column(name = "salida_codigo_ciudad")
    private Integer salidaCodigoCiudad;

    @Column(name = "salida_ciudad")
    private String salidaCiudad;

    @Column(name = "entrega_direccion")
    private String entregaDireccion;

    @Column(name = "entrega_departamento")
    private String entregaDepartamento;

    @Column(name = "entrega_codigo_ciudad")
    private Integer entregaCodigoCiudad;

    @Column(name = "entrega_ciudad")
    private String entregaCiudad;

    // --- transporte ---
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_transporte", length = 20)
    private TipoTransporteNr tipoTransporte;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_transporte", length = 20)
    private ModalidadTransporteNr modalidadTransporte;

    @Column(name = "transportista_nombre")
    private String transportistaNombre;

    @Column(name = "transportista_ruc")
    private String transportistaRuc;

    @Column(name = "transportista_direccion")
    private String transportistaDireccion;

    // --- vehículo y chofer: FK al catálogo + snapshot ---
    @Column(name = "vehiculo_id")
    private Long vehiculoId;

    @Column(name = "vehiculo_marca")
    private String vehiculoMarca;

    @Column(name = "vehiculo_matricula")
    private String vehiculoMatricula;

    @Column(name = "chofer_persona_id")
    private Long choferPersonaId;

    @Column(name = "chofer_nombre")
    private String choferNombre;

    @Column(name = "chofer_documento")
    private String choferDocumento;

    @Column(name = "chofer_direccion")
    private String choferDireccion;

    private Boolean activo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}
