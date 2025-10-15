package com.franco.dev.domain.financiero;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.UpdateTimestamp;

import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TypeDef(
  name = "estado_lote_de_enum",
  typeClass = PostgreSQLEnumType.class
)
@Entity
@Table(name = "lote_de", schema = "financiero")
public class LoteDE implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Type(type = "estado_lote_de_enum")
  @Column(name = "estado")
  private EstadoLoteDE estado;
  @Column(name = "fecha_procesado")
  private LocalDateTime fechaProcesado;

  @Column(name = "fecha_ultimo_intento")
  private LocalDateTime fechaUltimoIntento;

  private Integer intentos;

  @Column(columnDefinition = "TEXT", name = "respuesta_sifen")
  private String respuestaSifen;

  private String protocolo;

  private Boolean aprobado;

  private Boolean activo;

  @OneToMany(mappedBy = "loteDe", fetch = FetchType.LAZY)
  private List<DocumentoElectronico> documentosElectronicos;

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