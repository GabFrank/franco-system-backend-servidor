package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "vale", schema = "rrhh")
public class Vale implements Identifiable<Long> {

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
    @JoinColumn(name = "funcionario_id", nullable = true)
    private Funcionario funcionario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_id", nullable = true)
    private MotivoVale motivo;

    private BigDecimal monto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    private ValeEstado estado;

    @Column(name = "es_adelanto")
    private Boolean esAdelanto;

    // FKs planas (patron movimiento_id de Gourmet)
    @Column(name = "liquidacion_id")
    private Long liquidacionId;


    @Column(name = "caja_virtual_id")
    private Long cajaVirtualId;

    /**
     * Obligacion de pago del vale cuando se paga desde tesoreria (motor CPP).
     * Null = vale del atajo viejo (crearValeConfirmado: egreso directo a caja mayor).
     */
    @Column(name = "solicitud_pago_id")
    private Long solicitudPagoId;

    @Column(name = "movimiento_caja_virtual_id")
    private Long movimientoCajaVirtualId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Usuario autorizadoPor;

    private String observacion;

    @Column(name = "comprobante_url")
    private String comprobanteUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
