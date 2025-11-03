package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Cliente;
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
import java.math.BigDecimal;
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
@Table(name = "evento_nominacion_de", schema = "financiero")
public class EventoNominacionDE implements Identifiable<Long>, Serializable {

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

    @Column(name = "documento_electronico_id", nullable = false)
    private Long documentoElectronicoId;

    // Relación con el documento electrónico nominado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "documento_electronico_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "sucursal_id", referencedColumnName = "sucursal_id", insertable = false, updatable = false)
    })
    private DocumentoElectronico documentoElectronico;

    // Relación con la sucursal (para replicación)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false, insertable = false, updatable = false)
    private Sucursal sucursal;

    // Datos del evento de nominación enviado
    @Column(name = "evento_id")
    private String eventoId;           // ID único del evento
    
    @Column(name = "fecha_firma")
    private LocalDateTime fechaFirma;   // Fecha de firma del evento
    
    @Column(name = "cdc_documento")
    private String cdcDocumento;        // CDC del documento que se está nominando
    
    // Datos del receptor nominado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;            // Cliente nominado como receptor
    
    @Column(name = "nombre_receptor")
    private String nombreReceptor;      // Nombre del receptor nominado
    
    @Column(name = "documento_receptor")
    private String documentoReceptor;   // Documento del receptor nominado
    
    @Column(name = "tipo_receptor")
    private String tipoReceptor;        // CONTRIBUYENTE / NO_CONTRIBUYENTE
    
    @Column(name = "total_factura")
    private BigDecimal totalFactura;    // Total de la factura nominada
    
    @Column(name = "fecha_emision")
    private LocalDateTime fechaEmision; // Fecha de emisión de la factura
    
    @Column(name = "fecha_recepcion")
    private LocalDateTime fechaRecepcion; // Fecha de recepción por el receptor
    
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

