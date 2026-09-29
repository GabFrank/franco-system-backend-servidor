package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.rrhh.enums.ValeCuotaEstado;
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
 * Cuota de un vale que se descuenta en varias liquidaciones (vale.cantidadCuotas > 1).
 * La cuota se descuenta en la liquidacion cuyo periodo contiene {@code fechaDescuento}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "vale_cuota", schema = "rrhh")
public class ValeCuota implements Identifiable<Long> {

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
    @JoinColumn(name = "vale_id", nullable = false)
    private Vale vale;

    private Integer numero;

    private BigDecimal monto;

    @Column(name = "fecha_descuento")
    private LocalDate fechaDescuento;

    @Enumerated(EnumType.STRING)
    private ValeCuotaEstado estado;

    // FKs planas: la liquidacion mensual o el finiquito que la desconto.
    @Column(name = "liquidacion_id")
    private Long liquidacionId;

    @Column(name = "liquidacion_final_id")
    private Long liquidacionFinalId;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
