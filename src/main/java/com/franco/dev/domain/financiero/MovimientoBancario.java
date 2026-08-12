package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Movimiento (ledger) de una cuenta bancaria. Independiente de la caja mayor. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "movimiento_bancario", schema = "financiero")
public class MovimientoBancario implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_bancaria_id", nullable = false)
    private CuentaBancaria cuentaBancaria;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento", length = 30)
    private MovimientoBancarioTipo tipoMovimiento;

    private BigDecimal monto;

    @Column(name = "saldo_anterior")
    private BigDecimal saldoAnterior;

    @Column(name = "saldo_posterior")
    private BigDecimal saldoPosterior;

    private String descripcion;

    @Column(name = "numero_comprobante")
    private String numeroComprobante;

    @Column(name = "origen_tipo", length = 40)
    private String origenTipo;

    @Column(name = "origen_id")
    private Long origenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean anulado;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
