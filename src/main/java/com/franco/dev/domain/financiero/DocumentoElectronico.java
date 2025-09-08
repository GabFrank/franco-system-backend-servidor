package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "documento_electronico", schema = "financiero")
public class DocumentoElectronico implements Identifiable<Long>, Serializable {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private LoteDte lote;

    @Column(name = "cdc", unique = true)
    private String cdc;

    @Column(name = "estado_sifen")
    private String estadoSifen;

    @Column(name = "mensaje_sifen", columnDefinition = "TEXT")
    private String mensajeSifen;

    @Column(name = "xml_firmado", columnDefinition = "TEXT")
    private String xmlFirmado;

    @Column(name = "url_qr")
    private String urlQr;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "factura_legal_id", referencedColumnName = "id"),
            @JoinColumn(name = "sucursal_id", referencedColumnName = "sucursal_id")
    })
    private FacturaLegal facturaLegal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", insertable = false, updatable = false)
    private Sucursal sucursal;

    @OneToMany(mappedBy = "documentoElectronico", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<EventoDte> eventos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}


