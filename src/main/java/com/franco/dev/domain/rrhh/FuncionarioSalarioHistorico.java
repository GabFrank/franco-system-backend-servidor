package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Trazabilidad de cambios salariales del funcionario. Espejo de Gourmet
 * (historico_salarios). Snapshottea el salario en numeric(18,2) — Funcionario.sueldo
 * es Float y no se puede cambiar el tipo (regla Flyway §20 #3). Aditivo, central-only.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "funcionario_salario_historico", schema = "rrhh")
public class FuncionarioSalarioHistorico implements Identifiable<Long> {

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

    @Column(name = "salario_anterior")
    private BigDecimal salarioAnterior;

    @Column(name = "salario_nuevo")
    private BigDecimal salarioNuevo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    @Column(name = "fecha_vigencia")
    private LocalDate fechaVigencia;

    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Usuario autorizadoPor;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
