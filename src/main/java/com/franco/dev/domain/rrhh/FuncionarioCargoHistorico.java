package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Trazabilidad de cambios de cargo de un funcionario. Cada cambio crea un
 * registro nuevo y cierra el anterior con fecha_hasta. Espejo de Gourmet
 * (historico_cargos). Aditivo, central-only.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "funcionario_cargo_historico", schema = "rrhh")
public class FuncionarioCargoHistorico implements Identifiable<Long> {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id", nullable = true)
    private Cargo cargo;

    @Column(name = "fecha_desde")
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Usuario autorizadoPor;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
