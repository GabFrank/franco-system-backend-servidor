package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.VacacionVentaEstado;
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
@Table(name = "vacacion_venta", schema = "rrhh")
public class VacacionVenta implements Identifiable<Long> {

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
    @JoinColumn(name = "vacacion_id", nullable = true)
    private Vacacion vacacion;

    private Integer dias;

    private BigDecimal monto;

    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    private VacacionVentaEstado estado;

    @Column(name = "liquidacion_id")
    private Long liquidacionId;

    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Usuario autorizadoPor;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
