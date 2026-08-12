package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoAcreditacionPos;
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

/**
 * Acreditación de una venta con tarjeta a una cuenta bancaria. Nace PENDIENTE; el
 * scheduler la acredita al vencer los minutos configurados en la terminal, luego se
 * verifica manualmente contra el extracto (ajuste diferencial).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "acreditacion_pos", schema = "financiero")
public class AcreditacionPos implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terminal_pos_id", nullable = true)
    private TerminalPos terminalPos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_bancaria_id", nullable = true)
    private CuentaBancaria cuentaBancaria;

    @Column(name = "monto_original") private BigDecimal montoOriginal;
    @Column(name = "monto_comision") private BigDecimal montoComision;
    @Column(name = "monto_esperado") private BigDecimal montoEsperado;
    @Column(name = "monto_acreditado") private BigDecimal montoAcreditado;

    @Column(name = "fecha_transaccion") private LocalDateTime fechaTransaccion;
    @Column(name = "fecha_esperada_acreditacion") private LocalDateTime fechaEsperadaAcreditacion;
    @Column(name = "fecha_acreditacion_real") private LocalDateTime fechaAcreditacionReal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20)
    private EstadoAcreditacionPos estado;

    private BigDecimal diferencia;
    @Column(name = "venta_id") private Long ventaId;
    @Column(name = "movimiento_bancario_id") private Long movimientoBancarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
