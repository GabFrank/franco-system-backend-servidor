package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "liquidacion_item", schema = "rrhh")
public class LiquidacionItem implements Identifiable<Long> {

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
    @JoinColumn(name = "liquidacion_id", nullable = true)
    private LiquidacionSueldo liquidacion;

    private String codigo;

    private String descripcion;

    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private LiquidacionItemTipo tipo;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "referencia_tipo")
    private String referenciaTipo;

    /** Sucursal de la referencia cuando su PK es compuesta (ej. venta_credito_cuota). */
    @Column(name = "referencia_sucursal_id")
    private Long referenciaSucursalId;

    /** Estado previo del origen (ej. VentaCredito) antes de cobrarlo, para revertir al anular. */
    @Column(name = "referencia_estado_previo")
    private String referenciaEstadoPrevio;

    private Boolean manual = false;

    // --- Auditoría de edición (todo es negociable) ---
    private Boolean editado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editado_por_id")
    private Usuario editadoPor;

    @Column(name = "editado_en")
    private LocalDateTime editadoEn;

    /** Monto original, antes de la primera edición manual. */
    @Column(name = "monto_original")
    private BigDecimal montoOriginal;

    // --- Moneda del origen (V283.1). `monto` sigue en la moneda de la liquidación. ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id")
    private Moneda moneda;

    /** El monto en {@link #moneda}: lo que se descuenta de la cuota o el vale de origen. */
    @Column(name = "monto_moneda")
    private BigDecimal montoMoneda;

    /** Factor origen → moneda de la liquidación, sin redondear: monto = montoMoneda × cotizacion. */
    private BigDecimal cotizacion;

    /** Cantidad contra el origen; un ítem anterior a V283.1 está en la moneda de la liquidación. */
    public BigDecimal montoEnOrigen() {
        return montoMoneda != null ? montoMoneda : monto;
    }
}
