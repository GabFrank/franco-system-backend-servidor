package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.JornadaNovedadTipo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cubre los estados de asistencia que la Jornada (administrativo) no modela
 * (VACACION, JUSTIFICADO, FERIADO, etc.) sin tocar la Jornada.
 * Referencia a la jornada via FK plana (jornada_id + sucursal_id) porque
 * Jornada tiene PK compuesta.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "jornada_novedad", schema = "rrhh")
public class JornadaNovedad implements Identifiable<Long> {

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

    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    private JornadaNovedadTipo tipo;

    @Column(name = "jornada_id")
    private Long jornadaId;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id", nullable = true)
    private Usuario registradoPor;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
