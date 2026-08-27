package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.AguinaldoEstado;
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
@Table(name = "aguinaldo", schema = "rrhh")
public class Aguinaldo implements Identifiable<Long> {

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

    private Integer anio;

    @Column(name = "monto_calculado")
    private BigDecimal montoCalculado;

    @Column(name = "monto_proyectado")
    private BigDecimal montoProyectado;

    /** Meses que va a haber trabajado al 31/12; con el anio cerrado coincide con mesesTrabajados. */
    @Column(name = "meses_proyectados")
    private Integer mesesProyectados;

    @Column(name = "meses_trabajados")
    private Integer mesesTrabajados;

    @Enumerated(EnumType.STRING)
    private AguinaldoEstado estado;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "liquidacion_id")
    private Long liquidacionId;

    /** Pago separado (fuera de la liquidación mensual): trazabilidad del egreso de Caja Mayor. */
    @Column(name = "caja_virtual_id")
    private Long cajaVirtualId;

    /**
     * Obligacion de pago (SolicitudPago tipo RRHH) cuando se paga desde el hub de la caja.
     * NULL = pagada por el atajo viejo (egreso directo desde la pantalla de RRHH).
     */
    @Column(name = "solicitud_pago_id")
    private Long solicitudPagoId;

    @Column(name = "movimiento_caja_virtual_id")
    private Long movimientoCajaVirtualId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    /**
     * De donde salio el monto: {@code PERCIBIDO} (suma de las liquidaciones del anio) o
     * {@code SUELDO_ACTUAL} (fallback cuando el funcionario no tiene ninguna). NULL en las
     * filas calculadas antes de que esto existiera.
     */
    @Column(name = "origen_base")
    private String origenBase;

    /** Sobre cuantos meses se calculo. Menos meses que los devengados y con un hueco en el medio = falta cargar una liquidacion. */
    @Column(name = "meses_con_liquidacion")
    private Integer mesesConLiquidacion;

    /**
     * El {@code montoCalculado} que habia antes del ultimo recalculo.
     *
     * <p>Recalcular pisa el monto, y el rollback del JAR no devuelve un dato ya escrito.
     * Sin este campo, un recalculo con una formula equivocada no se puede deshacer.</p>
     */
    @Column(name = "monto_anterior")
    private BigDecimal montoAnterior;

    @Column(name = "recalculado_en")
    private LocalDateTime recalculadoEn;
}
