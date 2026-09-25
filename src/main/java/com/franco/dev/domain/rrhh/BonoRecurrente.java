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
import java.time.LocalDateTime;

/**
 * Plantilla de bono recurrente. No es un bono: define que bono hay que crear
 * cada periodo. El bono real lo genera BonoRecurrenteService en rrhh.bono.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "bono_recurrente", schema = "rrhh")
public class BonoRecurrente implements Identifiable<Long> {

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
    @JoinColumn(name = "funcionario_id", nullable = false)
    private Funcionario funcionario;

    @Enumerated(EnumType.STRING)
    private BonoTipo tipo;

    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private BonoFrecuencia frecuencia;

    private String motivo;

    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Usuario autorizadoPor;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
