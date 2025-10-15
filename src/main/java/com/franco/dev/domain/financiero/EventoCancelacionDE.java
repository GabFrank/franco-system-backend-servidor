package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TypeDef(
        name = "estado_evento_enum",
        typeClass = PostgreSQLEnumType.class
)
@Entity
@Table(name = "evento_cancelacion_de", schema = "financiero")
public class EventoCancelacionDE implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con el documento electrónico cancelado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_electronico_id", nullable = false)
    private DocumentoElectronico documentoElectronico;

    // Datos del evento de cancelación enviado
    @Column(name = "evento_id")
    private String eventoId;           // ID único del evento
    
    @Column(name = "fecha_firma")
    private LocalDateTime fechaFirma;   // Fecha de firma del evento
    
    @Column(name = "cdc_documento")
    private String cdcDocumento;        // CDC del documento que se está cancelando
    
    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;   // Motivo de la cancelación
    
    @Column(columnDefinition = "TEXT", name = "xml_evento")
    private String xmlEvento;           // XML del evento enviado

    // Respuesta de SIFEN
    @Enumerated(EnumType.STRING)
    @Type(type = "estado_evento_enum")
    @Column(columnDefinition = "financiero.estado_evento_enum")
    private EstadoEvento estado;        // PENDIENTE, APROBADO, RECHAZADO
    
    @Column(name = "fecha_procesamiento")
    private LocalDateTime fechaProcesamiento; // Fecha de procesamiento por SIFEN
    
    @Column(name = "protocolo_autorizacion")
    private String protocoloAutorizacion;     // Protocolo de autorización de SIFEN
    
    @Column(name = "codigo_respuesta")
    private String codigoRespuesta;           // Código de respuesta (ej: 0600)
    
    @Column(name = "mensaje_respuesta", columnDefinition = "TEXT")
    private String mensajeRespuesta;          // Mensaje de respuesta
    
    @Column(columnDefinition = "TEXT", name = "respuesta_bruta")
    private String respuestaBruta;      // Respuesta completa de SIFEN (XML)

    // Auditoría
    private Boolean activo;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}

