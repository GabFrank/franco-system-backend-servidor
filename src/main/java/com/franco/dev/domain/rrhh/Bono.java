package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
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
@Table(name = "bono", schema = "rrhh")
public class Bono implements Identifiable<Long> {

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

    @Enumerated(EnumType.STRING)
    private BonoTipo tipo;

    private BigDecimal monto;

    private LocalDate fecha;

    private String motivo;

    @Column(name = "es_recurrente")
    private Boolean esRecurrente;

    @Enumerated(EnumType.STRING)
    private BonoFrecuencia frecuencia;

    private Boolean anulado;

    @Column(name = "liquidacion_id")
    private Long liquidacionId;

    /** Plantilla que genero este bono. NULL en los bonos cargados a mano. */
    @Column(name = "bono_recurrente_id")
    private Long bonoRecurrenteId;

    /** "YYYY-MM". Clave de idempotencia del generador: no se mueve aunque
     *  despues alguien edite la fecha del bono. NULL en los bonos manuales. */
    @Column(name = "periodo")
    private String periodo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Usuario autorizadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
