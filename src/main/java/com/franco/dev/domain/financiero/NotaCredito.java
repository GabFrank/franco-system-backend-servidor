package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaCredito;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Nota de Crédito Electrónica (NCE, iTiDE 5): acredita total o parcialmente una factura electrónica.
 *
 * Central-only y con PK compuesta {@code (id, sucursal_id)}. El id lo asigna el service desde la
 * secuencia: con {@code @IdClass}, {@code @GeneratedValue} no puede insertar (§13.11 del plan).
 *
 * **La moneda se hereda de la factura**, no se elige: una NC en otra moneda que su factura es un
 * rechazo de SIFEN. Los totales se copian tal cual para no introducir diferencias de redondeo.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "nota_credito", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class NotaCredito implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "timbrado_detalle_id", nullable = false)
    private Long timbradoDetalleId;

    /** Serie propia de la nota de crédito dentro del timbrado. */
    @Column(name = "numero_nota_credito", nullable = false)
    private Integer numeroNotaCredito;

    private LocalDateTime fecha;

    /** La factura que se acredita. Obligatoria: no hay NC sin documento asociado en este alcance. */
    @Column(name = "factura_legal_id", nullable = false)
    private Long facturaLegalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_emision", nullable = false, length = 50)
    private MotivoEmisionNotaCredito motivoEmision;

    /** Texto libre; sale solo en el KuDE. El XML lleva la descripción que genera la librería. */
    @Column(name = "descripcion_motivo")
    private String descripcionMotivo;

    @Column(name = "cliente_id")
    private Long clienteId;

    private String nombre;

    private String ruc;

    private String direccion;

    @Column(name = "moneda_extranjera")
    private String monedaExtranjera;

    @Column(name = "tipo_cambio")
    private BigDecimal tipoCambio;

    @Column(name = "iva_parcial_0")
    private BigDecimal ivaParcial0;

    @Column(name = "iva_parcial_5")
    private BigDecimal ivaParcial5;

    @Column(name = "iva_parcial_10")
    private BigDecimal ivaParcial10;

    @Column(name = "total_parcial_0")
    private BigDecimal totalParcial0;

    @Column(name = "total_parcial_5")
    private BigDecimal totalParcial5;

    @Column(name = "total_parcial_10")
    private BigDecimal totalParcial10;

    private BigDecimal descuento;

    @Column(name = "total_final")
    private BigDecimal totalFinal;

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
