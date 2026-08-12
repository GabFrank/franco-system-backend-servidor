package com.franco.dev.domain.financiero;

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
 * Saldo de una caja virtual (efectivo) por moneda. Fuente de verdad del saldo
 * en el modelo cash-only: una fila por cada par (caja, moneda). Reemplaza las
 * columnas fijas {@code saldo_gs/rs/ds} de {@code CajaVirtual}, que quedan como
 * shim derivado por compatibilidad hasta que la UI se porte.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "caja_virtual_saldo", schema = "financiero",
        uniqueConstraints = @UniqueConstraint(name = "uq_caja_virtual_saldo", columnNames = {"caja_virtual_id", "moneda_id"}))
public class CajaVirtualSaldo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_virtual_id", nullable = false)
    private CajaVirtual cajaVirtual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = false)
    private Moneda moneda;

    @Column(name = "saldo", nullable = false)
    private BigDecimal saldo = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
