package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Justificativo de un dia: explica por que ese dia no fue una jornada normal
 * (vacacion, permiso, reposo medico, media falta, ...). Antes se llamaba
 * "novedad de jornada", nombre que no reflejaba su funcion.
 *
 * El comportamiento (evitar penalizacion, descontar salario) lo define el
 * TipoJustificativo, no esta entidad.
 *
 * Referencia a la jornada via FK plana (jornada_id + sucursal_id) porque
 * Jornada tiene PK compuesta.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "justificativo", schema = "rrhh")
public class Justificativo implements Identifiable<Long> {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_justificativo_id", nullable = true)
    private TipoJustificativo tipo;

    @Column(name = "jornada_id")
    private Long jornadaId;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    private String observacion;

    /**
     * Documento respaldatorio del justificativo (reposo medico, duelo, etc).
     * Obligatorio cuando el tipo tiene requiereDocumento = true.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = true)
    private FuncionarioDocumento documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id", nullable = true)
    private Usuario registradoPor;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
