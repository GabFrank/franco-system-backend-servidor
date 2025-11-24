package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
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
@Table(name = "evento_inutilizacion_de", schema = "financiero")
public class EventoInutilizacionDE implements Identifiable<Long>, Serializable {

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

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "timbrado_id", nullable = false)
    private Long timbradoId;

    @Column(name = "timbrado_detalle_id")
    private Long timbradoDetalleId;

    // Relación con el timbrado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timbrado_id", nullable = false, insertable = false, updatable = false)
    private Timbrado timbrado;

    // Relación con el timbrado detalle
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "timbrado_detalle_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "sucursal_id", referencedColumnName = "sucursal_id", insertable = false, updatable = false)
    })
    private TimbradoDetalle timbradoDetalle;

    // Relación con la sucursal (para replicación)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false, insertable = false, updatable = false)
    private Sucursal sucursal;

    // Datos del evento de inutilización enviado
    @Column(name = "evento_id", nullable = false)
    private String eventoId;           // ID único del evento
    
    @Column(name = "fecha_firma", nullable = false)
    private LocalDateTime fechaFirma;   // Fecha de firma del evento
    
    @Column(name = "establecimiento", nullable = false, length = 10)
    private String establecimiento;      // Establecimiento (ej: "001")
    
    @Column(name = "punto_expedicion", nullable = false, length = 10)
    private String puntoExpedicion;     // Punto de expedición
    
    @Column(name = "numero_inicio", nullable = false)
    private Integer numeroInicio;       // Número inicial del rango
    
    @Column(name = "numero_fin", nullable = false)
    private Integer numeroFin;          // Número final del rango
    
    @Column(name = "tipo_de", nullable = false, length = 50)
    private String tipoDE;              // Tipo de documento electrónico (ej: "FACTURA_ELECTRONICA")
    
    @Column(name = "motivo_inutilizacion", nullable = false, columnDefinition = "TEXT")
    private String motivoInutilizacion; // Motivo de la inutilización
    
    @Column(columnDefinition = "TEXT", name = "xml_evento")
    private String xmlEvento;           // XML del evento enviado

    // Respuesta de SIFEN
    @Enumerated(EnumType.STRING)
    @Type(type = "estado_evento_enum")
    @Column(columnDefinition = "financiero.estado_evento_enum", nullable = false)
    private EstadoEvento estado;        // PENDIENTE, APROBADO, RECHAZADO, ERROR_ENVIO
    
    @Column(name = "fecha_procesamiento")
    private LocalDateTime fechaProcesamiento; // Fecha de procesamiento por SIFEN
    
    @Column(name = "protocolo_autorizacion", length = 100)
    private String protocoloAutorizacion;     // Protocolo de autorización de SIFEN
    
    @Column(name = "codigo_respuesta", length = 10)
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

