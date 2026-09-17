package com.franco.dev.domain.financiero;

import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.UpdateTimestamp;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TypeDef(
  name = "estado_de_enum",
  typeClass = PostgreSQLEnumType.class
)
@Entity
@Table(name = "documento_electronico", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class DocumentoElectronico implements Serializable{
  
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Id
  @Column(name = "sucursal_id", nullable = false)
  private Long sucursalId;

  // Relación con sucursal (para replicación)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sucursal_id", nullable = false, insertable = false, updatable = false)
  private Sucursal sucursal;

  /**
   * Columna plana escribible de la FK a la factura. Las relaciones de abajo son de solo lectura
   * ({@code insertable = false}), asi que sin estas columnas Hibernate NO escribe ninguna FK:
   * el INSERT de la entidad no las incluia y crear un DE desde central fallaba con
   * "null value in column factura_legal_id" (spike 2026-09-17, §13.1 del plan).
   * Queda NULL en las notas de credito y de remision.
   */
  @Column(name = "factura_legal_id")
  private Long facturaLegalId;

  /** FK a la nota de credito que origina este DE. Excluyente con las otras dos. */
  @Column(name = "nota_credito_id")
  private Long notaCreditoId;

  /** FK a la nota de remision que origina este DE. Excluyente con las otras dos. */
  @Column(name = "nota_remision_id")
  private Long notaRemisionId;

  /** Columna plana escribible del lote; la relacion de abajo es de solo lectura. */
  @Column(name = "lote_de_id")
  private Long loteDeId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumns({
    @JoinColumn(name = "factura_legal_id", referencedColumnName = "id", insertable = false, updatable = false),
    @JoinColumn(name = "sucursal_id", referencedColumnName = "sucursal_id", insertable = false, updatable = false)
  })
  private FacturaLegal facturaLegal;

  /** Mantiene la columna plana en sincronia con la relacion: sin esto la FK no se persiste. */
  public void setFacturaLegal(FacturaLegal facturaLegal) {
    this.facturaLegal = facturaLegal;
    this.facturaLegalId = facturaLegal != null ? facturaLegal.getId() : null;
  }

  // Información del documento electrónico
  private String cdc;

  private String urlQr;
  
  @Column(columnDefinition = "TEXT")
  private String xmlFirmado;

  @Column(columnDefinition = "TEXT")
  private String xmlOriginal;
      
  // Estado del documento
  @Enumerated(EnumType.STRING)
  @Type(type = "estado_de_enum")
  @Column(columnDefinition = "financiero.estado_de_enum")
  private EstadoDE estado;
  
  @Column(name = "codigo_respuesta_sifen")
  private String codigoRespuestaSifen;
  
  @Column(name = "mensaje_respuesta_sifen")
  private String mensajeRespuestaSifen;
      
  // Información adicional
  @Column(name = "numero_documento")
  private String numeroDocumento;
  
  @Column(name = "tipo_documento")
  private String tipoDocumento;
  
  @Column(name = "fecha_emision")
  private LocalDateTime fechaEmision;
  
  @Column(name = "fecha_recepcion_sifen")
  private LocalDateTime fechaRecepcionSifen;
  
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumns({
    @JoinColumn(name = "lote_de_id", referencedColumnName = "id", insertable = false, updatable = false),
    @JoinColumn(name = "sucursal_id", referencedColumnName = "sucursal_id", insertable = false, updatable = false)
  })
  private LoteDE loteDe;

  /** Idem {@link #setFacturaLegal}: sin esto vincularDocumentosALote no escribia lote_de_id. */
  public void setLoteDe(LoteDE loteDe) {
    this.loteDe = loteDe;
    this.loteDeId = loteDe != null ? loteDe.getId() : null;
  }
      
  // Campos de auditoría
  private Boolean activo;
  
  @CreationTimestamp
  @Column(name = "creado_en")
  private LocalDateTime creadoEn;
  
  @UpdateTimestamp
  @Column(name = "actualizado_en")
  private LocalDateTime actualizadoEn;
  
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "usuario_id", nullable = true)
  private Usuario usuario;

}
    
