package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.MovimientoProveedorTipo;
import com.franco.dev.domain.personas.Proveedor;
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

/** Libro (ledger) de cuenta corriente de un proveedor. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "movimiento_proveedor", schema = "financiero")
public class MovimientoProveedor implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20)
    private MovimientoProveedorTipo tipo;

    private BigDecimal monto;
    @Column(name = "saldo_anterior") private BigDecimal saldoAnterior;
    @Column(name = "saldo_posterior") private BigDecimal saldoPosterior;
    @Column(name = "solicitud_pago_id") private Long solicitudPagoId;
    @Column(name = "movimiento_caja_virtual_id") private Long movimientoCajaVirtualId;
    @Column(name = "movimiento_bancario_id") private Long movimientoBancarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    private BigDecimal cotizacion;
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean anulado;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
