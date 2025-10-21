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

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumns({
    @JoinColumn(name = "factura_legal_id", referencedColumnName = "id", insertable = false, updatable = false),
    @JoinColumn(name = "sucursal_id", referencedColumnName = "sucursal_id", insertable = false, updatable = false)
  })
  private FacturaLegal facturaLegal;

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
    
