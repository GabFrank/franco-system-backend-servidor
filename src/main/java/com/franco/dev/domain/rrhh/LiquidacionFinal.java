package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.domain.rrhh.enums.MotivoEgreso;
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

/**
 * Finiquito al egreso del funcionario (Fase 6, §4.8). Cabecera con el desglose
 * (indemnización + vacaciones no gozadas + aguinaldo proporcional) y sus items.
 * Central-only. Pagar → EGRESO en Caja Mayor + funcionario.activo=false.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "liquidacion_final", schema = "rrhh")
public class LiquidacionFinal implements Identifiable<Long> {

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

    @Column(name = "fecha_egreso")
    private LocalDate fechaEgreso;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_egreso")
    private MotivoEgreso motivoEgreso;

    @Column(name = "antiguedad_dias")
    private Integer antiguedadDias;

    @Column(name = "antiguedad_meses")
    private Integer antiguedadMeses;

    @Column(name = "antiguedad_anios")
    private Integer antiguedadAnios;

    @Column(name = "salario_promedio")
    private BigDecimal salarioPromedio;

    @Column(name = "indemnizacion_aplica")
    private Boolean indemnizacionAplica;

    @Column(name = "indemnizacion_monto")
    private BigDecimal indemnizacionMonto;

    @Column(name = "dias_vacaciones_no_gozadas")
    private Integer diasVacacionesNoGozadas;

    @Column(name = "monto_vacaciones_no_gozadas")
    private BigDecimal montoVacacionesNoGozadas;

    @Column(name = "aguinaldo_proporcional")
    private BigDecimal aguinaldoProporcional;

    @Column(name = "total_liquidado")
    private BigDecimal totalLiquidado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    @Enumerated(EnumType.STRING)
    private LiquidacionFinalEstado estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprobado_por_id", nullable = true)
    private Usuario aprobadoPor;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "caja_virtual_id")
    private Long cajaVirtualId;

    @Column(name = "movimiento_caja_virtual_id")
    private Long movimientoCajaVirtualId;


    private String observacion;

    @OneToMany(mappedBy = "liquidacionFinal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LiquidacionFinalItem> items;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
