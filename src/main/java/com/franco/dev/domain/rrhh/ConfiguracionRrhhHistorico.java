package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Auditoria de cambios de los parametros de RRHH.
 *
 * Estos parametros impactan la nomina (IPS, recargos de HE, dias de vacaciones,
 * salario minimo legal). Sin historial no hay forma de saber que valor regia
 * cuando se liquido un periodo, ni quien lo cambio.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_rrhh_historico", schema = "rrhh")
public class ConfiguracionRrhhHistorico implements Identifiable<Long> {

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

    @Column(name = "configuracion_rrhh_id")
    private Long configuracionRrhhId;

    private String clave;

    @Column(name = "valor_anterior")
    private String valorAnterior;

    @Column(name = "valor_nuevo")
    private String valorNuevo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
