package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDef(
        name = "solicitud_pago_estado",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "solicitud_pago", schema = "operaciones")
public class SolicitudPago implements Identifiable<Long> {

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
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "numero_solicitud", nullable = false)
    private String numeroSolicitud;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_pago_propuesta")
    private LocalDateTime fechaPagoPropuesta;

    @Column(name = "monto_total", nullable = false)
    private Double montoTotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = false)
    private Moneda moneda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pago_id", nullable = true)
    private FormaPago formaPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Type(type = "solicitud_pago_estado")
    private SolicitudPagoEstado estado;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id", nullable = true)
    private Pago pago;

    // Relationship with notas de recepcion
    @OneToMany(mappedBy = "solicitudPago", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SolicitudPagoNotaRecepcion> notasRecepcion;
}

