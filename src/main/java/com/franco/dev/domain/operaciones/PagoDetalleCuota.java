package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.operaciones.enums.PagoDetalleCuotaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDef(
        name = "pago_detalle_cuota_estado",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "pago_detalle_cuota", schema = "operaciones")
public class PagoDetalleCuota implements Identifiable<Long> {

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
    @JoinColumn(name = "pago_detalle_id", nullable = false)
    private PagoDetalle pagoDetalle;

    @Column(name = "referencia_id")
    private Double referenciaId;

    @Column(name = "numero_cuota")
    private Double numeroCuota;

    @Column(name = "fecha_vencimiento")
    private LocalDateTime fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "pago_detalle_cuota_estado")
    private PagoDetalleCuotaEstado estado;

    @Column(name = "total_pagado")
    private Double totalPagado;

    @Column(name = "total_final")
    private Double totalFinal;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
} 