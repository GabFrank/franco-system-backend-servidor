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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
