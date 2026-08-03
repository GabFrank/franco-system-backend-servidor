package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.TipoOperacionFinanciera;
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
 * Operación financiera: cambio de divisa, depósito/retiro bancario, transferencia
 * entre cajas mayor, o transferencia bancaria (banco→banco). Genera los movimientos
 * de caja y/o banco correspondientes según su tipo.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "operacion_financiera", schema = "financiero")
public class OperacionFinanciera implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacion", length = 40)
    private TipoOperacionFinanciera tipoOperacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = true)
    private OperacionFinancieraCategoria categoria;

    private String descripcion;

    // --- Origen ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_mayor_origen_id", nullable = true)
    private CajaVirtual cajaMayorOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_bancaria_origen_id", nullable = true)
    private CuentaBancaria cuentaBancariaOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_origen_id", nullable = true)
    private Moneda monedaOrigen;

    @Column(name = "monto_origen")
    private BigDecimal montoOrigen;

    // --- Destino ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_mayor_destino_id", nullable = true)
    private CajaVirtual cajaMayorDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_bancaria_destino_id", nullable = true)
    private CuentaBancaria cuentaBancariaDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_destino_id", nullable = true)
    private Moneda monedaDestino;

    @Column(name = "monto_destino")
    private BigDecimal montoDestino;

    private BigDecimal cotizacion;

    @Column(name = "numero_comprobante")
    private String numeroComprobante;

    // --- Diferencia (sobra/falta al cerrar la operación) ---
    /** Monto de la diferencia; positivo = sobra, negativo = falta. */
    private BigDecimal diferencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "diferencia_destino_tipo", length = 20)
    private com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo diferenciaDestinoTipo;

    @Column(name = "diferencia_observacion")
    private String diferenciaObservacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean anulado;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
