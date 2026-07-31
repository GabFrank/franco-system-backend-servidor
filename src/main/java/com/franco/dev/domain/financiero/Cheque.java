package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.operaciones.PagoDetalleCuota;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "cheque", schema = "financiero")
public class Cheque implements Identifiable<Long> {

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
    @JoinColumn(name = "chequera_id", nullable = true)
    private Chequera chequera;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_detalle_cuota_id", nullable = true)
    private PagoDetalleCuota pagoDetalleCuota;

    private Double numero;

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    private String orden;

    private String concepto;

    private Boolean diferido;

    private Double total;

    @javax.persistence.Enumerated(javax.persistence.EnumType.STRING)
    @Column(name = "estado", length = 20)
    private com.franco.dev.domain.financiero.enums.EstadoCheque estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @javax.persistence.JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    @ManyToOne(fetch = FetchType.LAZY)
    @javax.persistence.JoinColumn(name = "cuenta_bancaria_id", nullable = true)
    private CuentaBancaria cuentaBancaria;

    @Column(name = "fecha_cobro")
    private LocalDateTime fechaCobro;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    @Column(name = "movimiento_bancario_id")
    private Long movimientoBancarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firmante", nullable = true)
    private Persona firmante;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
} 