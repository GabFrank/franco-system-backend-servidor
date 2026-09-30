package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemProgramadoEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ítem cargado desde una liquidación para que se aplique en la de un periodo posterior. Entra como ítem
 * automático ({@link #REFERENCIA_TIPO}) al generar la liquidación de {@code periodo}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "liquidacion_item_programado", schema = "rrhh")
public class LiquidacionItemProgramado implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    /** referenciaTipo de los ítems de liquidación/finiquito que aplican un programado. */
    public static final String REFERENCIA_TIPO = "ITEM_PROGRAMADO";

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

    /** YYYY-MM */
    private String periodo;

    @Column(name = "liquidacion_concepto_id")
    private Long liquidacionConceptoId;

    private String codigo;

    private String descripcion;

    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private LiquidacionItemTipo tipo;

    @Enumerated(EnumType.STRING)
    private LiquidacionItemProgramadoEstado estado = LiquidacionItemProgramadoEstado.PENDIENTE;

    // FKs planas: la liquidación mensual o el finiquito que lo aplicó, y desde dónde se cargó.
    @Column(name = "liquidacion_id")
    private Long liquidacionId;

    @Column(name = "liquidacion_final_id")
    private Long liquidacionFinalId;

    @Column(name = "origen_liquidacion_id")
    private Long origenLiquidacionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
