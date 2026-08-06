package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.FuentePago;
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
 * Detalle (línea) de un pago de solicitud (CPP). Un pago mixto genera una línea por
 * cada fuente/moneda; guarda a qué movimiento de caja o banco se imputó, para la
 * trazabilidad línea→movimiento y la reversión determinística.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pago_solicitud_detalle", schema = "financiero")
public class PagoSolicitudDetalle implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitud_pago_id")
    private Long solicitudPagoId;

    /** Evento de pago (operaciones.pago) que consolidó este detalle. Ancla para anular todo el evento. */
    @Column(name = "pago_id")
    private Long pagoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuente", length = 20)
    private FuentePago fuente;

    @Column(name = "caja_virtual_id")
    private Long cajaVirtualId;

    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    @Column(name = "moneda_id")
    private Long monedaId;

    private BigDecimal monto;

    private BigDecimal cotizacion;

    @Column(name = "monto_solicitud")
    private BigDecimal montoSolicitud;

    @Column(name = "movimiento_caja_virtual_id")
    private Long movimientoCajaVirtualId;

    @Column(name = "movimiento_bancario_id")
    private Long movimientoBancarioId;

    /** Cheque emitido (fuente CHEQUE): ancla para anular/trazar el cheque de esta línea. */
    @Column(name = "cheque_id")
    private Long chequeId;

    /** Línea de ajuste (fuente AJUSTE): diferencia de cambio a favor (pagó de menos). */
    private Boolean descuento;

    /** Línea de ajuste (fuente AJUSTE): diferencia de cambio en contra (pagó de más). */
    private Boolean aumento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean anulado;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
