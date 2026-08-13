package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Nota de credito consolidada por retiro a proveedor. Reemplaza el registro
 * per-devolucion (Devolucion.nroNotaCredito/montoAcreditado) por una cabecera
 * unica ligada a un RetiroDevolucion (1:1). Sus lineas consolidan los items de
 * todas las devoluciones del retiro por producto.
 *
 * saldo/estado quedan preparados para el uso futuro: aplicar el credito a favor
 * de la empresa contra una compra futura (aun no implementado; ver TODO).
 *
 * Usado en:
 * - Desktop: Si (dialogo "Registrar credito" en el historial de retiros)
 * - Mobile: No (por ahora)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "nota_credito_devolucion", schema = "operaciones")
public class NotaCreditoDevolucion implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    public static final String ESTADO_DISPONIBLE = "DISPONIBLE";
    public static final String ESTADO_PARCIAL = "PARCIAL";
    public static final String ESTADO_USADO = "USADO";

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
    @JoinColumn(name = "retiro_id", nullable = false)
    private RetiroDevolucion retiro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    /** Numero de la nota de credito emitida por el proveedor (texto del comprobante). */
    @Column(name = "nro_nota_credito")
    private String nroNotaCredito;

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @Column(name = "monto_total")
    private Double montoTotal;

    /** Saldo a favor aun no consumido. Al crear == montoTotal. */
    @Column(name = "saldo")
    private Double saldo;

    /** DISPONIBLE | PARCIAL | USADO */
    @Column(name = "estado", nullable = false)
    private String estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
