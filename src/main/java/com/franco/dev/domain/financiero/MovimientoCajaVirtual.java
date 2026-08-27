package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "movimiento_caja_virtual", schema = "financiero")
@TypeDef(name = "caja_virtual_tipo_movimiento", typeClass = PostgreSQLEnumType.class)
public class MovimientoCajaVirtual implements Serializable {

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
    @JoinColumn(name = "caja_virtual_id", nullable = false)
    private CajaVirtual cajaVirtual;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento")
    @Type(type = "caja_virtual_tipo_movimiento")
    private CajaVirtualTipoMovimiento tipoMovimiento;

    private Double cantidad;

    @Column(name = "saldo_anterior")
    private Double saldoAnterior;

    @Column(name = "saldo_posterior")
    private Double saldoPosterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    @Column(name = "referencia_id")
    private Long referenciaId;

    /** Dominio dueño del movimiento (para bloqueo de anulación cross-módulo). */
    @Enumerated(EnumType.STRING)
    @Column(name = "origen_tipo", length = 40)
    private com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo origenTipo;

    /** Id de la entidad de origen (vale, préstamo, retiro, cuota, etc.). */
    @Column(name = "origen_id")
    private Long origenId;

    /**
     * Segunda mitad de la PK del documento de origen, cuando es compuesta. Null para los de
     * PK simple.
     *
     * Hace falta porque {@code origenId} solo no identifica un documento: Retiro tiene PK
     * (id, sucursalId) y el id no es global — cada filial numera desde 1, así que el mismo id
     * lo comparten decenas de sucursales. Una caja mayor recibe retiros de varias sucursales
     * por diseño, con lo cual buscar por (origenTipo, origenId) devuelve movimientos ajenos.
     */
    @Column(name = "origen_sucursal_id")
    private Long origenSucursalId;

    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_origen_id", nullable = true)
    private CajaVirtual cajaOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_destino_id", nullable = true)
    private CajaVirtual cajaDestino;

    private Boolean activo;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
