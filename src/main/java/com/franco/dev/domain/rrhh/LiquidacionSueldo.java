package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "liquidacion_sueldo", schema = "rrhh")
public class LiquidacionSueldo implements Identifiable<Long> {

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

    private String periodo;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    // Las columnas son NOT NULL DEFAULT 0, pero el borrador se persiste antes de
    // calcular los totales (necesita id para colgar los items) y un NULL explicito
    // pisa el DEFAULT de la columna. aplicarTotales() los sobreescribe despues.
    @Column(name = "salario_base")
    private BigDecimal salarioBase = BigDecimal.ZERO;

    @Column(name = "total_haberes")
    private BigDecimal totalHaberes = BigDecimal.ZERO;

    @Column(name = "total_descuentos")
    private BigDecimal totalDescuentos = BigDecimal.ZERO;

    @Column(name = "total_neto")
    private BigDecimal totalNeto = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    @Enumerated(EnumType.STRING)
    private LiquidacionSueldoEstado estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprobado_por_id", nullable = true)
    private Usuario aprobadoPor;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    // FKs planas al egreso real de dinero
    @Column(name = "caja_virtual_id")
    private Long cajaVirtualId;

    @Column(name = "movimiento_caja_virtual_id")
    private Long movimientoCajaVirtualId;

    @Column(name = "movimiento_persona_id")
    private Long movimientoPersonaId;

    private String observacion;

    @OneToMany(mappedBy = "liquidacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LiquidacionItem> items;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
