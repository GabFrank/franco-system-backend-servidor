package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entidad intermedia que vincula EventoInutilizacionDE con DocumentoElectronico.
 * Representa qué documentos electrónicos fueron afectados por un evento de inutilización.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "evento_inutilizacion_de_documento_electronico", schema = "financiero")
public class EventoInutilizacionDEDocumentoElectronico implements Identifiable<Long>, Serializable {

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

    @Column(name = "evento_inutilizacion_de_id", nullable = false)
    private Long eventoInutilizacionDeId;

    @Column(name = "evento_inutilizacion_de_sucursal_id", nullable = false)
    private Long eventoInutilizacionDeSucursalId;

    @Column(name = "documento_electronico_id", nullable = false)
    private Long documentoElectronicoId;

    @Column(name = "documento_electronico_sucursal_id", nullable = false)
    private Long documentoElectronicoSucursalId;

    // Relación con EventoInutilizacionDE
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "evento_inutilizacion_de_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "evento_inutilizacion_de_sucursal_id", referencedColumnName = "sucursal_id", insertable = false, updatable = false)
    })
    private EventoInutilizacionDE eventoInutilizacion;

    // Relación con DocumentoElectronico
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "documento_electronico_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "documento_electronico_sucursal_id", referencedColumnName = "sucursal_id", insertable = false, updatable = false)
    })
    private DocumentoElectronico documentoElectronico;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}



