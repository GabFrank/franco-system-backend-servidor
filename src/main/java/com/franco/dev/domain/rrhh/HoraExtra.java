package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.HoraExtraOrigen;
import com.franco.dev.domain.rrhh.enums.HoraExtraTipo;
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
@Table(name = "hora_extra", schema = "rrhh")
public class HoraExtra implements Identifiable<Long> {

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

    // FK plana a la jornada (PK compuesta) de origen, si aplica.
    @Column(name = "jornada_id")
    private Long jornadaId;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    private BigDecimal minutos;

    @Enumerated(EnumType.STRING)
    private HoraExtraTipo tipo;

    @Column(name = "recargo_porcentaje")
    private BigDecimal recargoPorcentaje;

    @Column(name = "monto_calculado")
    private BigDecimal montoCalculado;

    @Enumerated(EnumType.STRING)
    private HoraExtraOrigen origen;

    private Boolean anulada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Usuario autorizadoPor;

    private String observacion;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
