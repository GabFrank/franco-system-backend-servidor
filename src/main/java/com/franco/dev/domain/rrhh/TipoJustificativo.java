package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.DescuentoJustificativo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Catalogo de tipos de justificativo. Cada tipo define su propio comportamiento,
 * por eso se pueden agregar tipos nuevos sin desplegar codigo:
 * - evitaPenalizacion: el job de penalizacion no penaliza esa jornada.
 * - descuentaSalario:  cuanto descuenta la liquidacion (NO / MEDIO_DIA / DIA_COMPLETO).
 * - requiereDocumento: exige respaldo (reposo medico, duelo, ...).
 * - generadoPorSistema: lo emite otro modulo (vacaciones/feriados), no se carga a mano.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tipo_justificativo", schema = "rrhh")
public class TipoJustificativo implements Identifiable<Long> {

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

    private String nombre;

    private String descripcion;

    @Column(name = "evita_penalizacion")
    private Boolean evitaPenalizacion = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "descuenta_salario")
    private DescuentoJustificativo descuentaSalario = DescuentoJustificativo.NO;

    @Column(name = "requiere_documento")
    private Boolean requiereDocumento = Boolean.FALSE;

    @Column(name = "generado_por_sistema")
    private Boolean generadoPorSistema = Boolean.FALSE;

    private Boolean activo = Boolean.TRUE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
