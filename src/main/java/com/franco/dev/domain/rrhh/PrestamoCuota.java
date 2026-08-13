package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
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
@Table(name = "prestamo_cuota", schema = "rrhh")
public class PrestamoCuota implements Identifiable<Long> {

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
    @JoinColumn(name = "prestamo_id", nullable = true)
    private Prestamo prestamo;

    private Integer numero;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    private BigDecimal monto;

    @Column(name = "monto_pagado")
    private BigDecimal montoPagado;

    @Enumerated(EnumType.STRING)
    private PrestamoCuotaEstado estado;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
