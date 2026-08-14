package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.TipoMovimientoPersonas;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @deprecated Tabla write-only: se escribe pero ningun flujo la lee. El unico calculo
 * agregado (getSaldoPorPersona -> getTotalCredito) tiene su unico llamador comentado en
 * ClienteResolver; el saldo del cliente sale de VentaCredito con estado ABIERTO.
 *
 * RRHH se desvinculo en 2026-07 (vale, prestamo, liquidacion y finiquito ya no la
 * escriben): la deuda viva del prestamo sale de rrhh.prestamo_cuota, que es la misma
 * fuente que gobierna el cobro y que lee la liquidacion. Ademas mezclaba criterios de
 * signo — VENTA_CREDITO guarda negativo y los tipos de RRHH positivo, en la misma columna.
 *
 * Hoy solo la escribe VentaCredito. Candidata a eliminarse: ver issue #159.
 * No agregar escrituras nuevas.
 */
@Deprecated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "movimiento_personas", schema = "financiero")
@TypeDef(
        name = "tipo_movimiento_personas",
        typeClass = PostgreSQLEnumType.class
)
public class MovimientoPersonas implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String observacion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "persona_id", nullable = true)
    private Persona persona;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    @Type(type = "tipo_movimiento_personas")
    private TipoMovimientoPersonas tipo;

    private Long referenciaId;

    private Double valorTotal;

    private Boolean activo;

    private LocalDateTime vencimiento;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



