package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ledger hijo de {@link MovimientoStock}: desglosa cada movimiento agregado en las cantidades
 * que corresponden a cada número de lote / fecha de vencimiento.
 *
 * NO es una tabla de saldos. El stock por lote se deriva sumando este ledger, igual que el stock
 * normal se deriva de SUM(movimiento_stock.cantidad). Ver la vista operaciones.v_stock_lote.
 *
 * INVARIANTE: para un mismo movimiento_stock, SUM(hijas.cantidad) = padre.cantidad, siempre en
 * unidades base.
 *
 * La FK compuesta (movimiento_stock_id, sucursal_id) tiene ON DELETE CASCADE en la base: cuando
 * se deshace una recepción y se borra el movimiento agregado, el desglose por lote se elimina
 * solo, sin lógica de reversión duplicada.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "movimiento_stock_lote", schema = "operaciones")
@IdClass(EmbebedPrimaryKey.class)
public class MovimientoStockLote implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Asignado siempre desde Java (ver MovimientoStockLoteService.save): el central genera IDs
     * IMPARES y la filial PARES, para evitar colisiones de PK bajo replicación lógica.
     */
    @Id
    private Long id;

    @Id
    @Column(name = "sucursal_id")
    private Long sucursalId;

    /**
     * Id del movimiento de stock agregado del que este registro es desglose.
     * Se guarda como Long y no como @ManyToOne porque MovimientoStock tiene PK compuesta.
     */
    @Column(name = "movimiento_stock_id", nullable = false)
    private Long movimientoStockId;

    /**
     * Lote al que pertenece este movimiento. Es la fuente de verdad del vencimiento y del estado.
     *
     * En el central la FK existe; en la filial el mismo vínculo va sin FK a propósito, porque el
     * maestro y el ledger viajan por publicaciones distintas y no hay garantía de orden entre
     * ellas (ver filial V82.3).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lote_id")
    private Lote lote;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Informativo: en qué presentación se recibió. La cantidad va igual en unidades base. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id")
    private Presentacion presentacion;

    @Column(name = "numero_lote", nullable = false)
    private String numeroLote;

    /**
     * @deprecated desde V155.3: la fecha de vencimiento vive en {@link Lote}. No escribir ni leer.
     * La columna se mantiene por la regla de 2 versiones de Flyway y se elimina más adelante.
     */
    @Deprecated
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    /** Positivo para entradas (COMPRA), negativo para salidas (VENTA). En unidades base. */
    @Column(name = "cantidad", nullable = false)
    private Double cantidad;

    /** Id de recepcion_mercaderia_item / venta_item según el origen del movimiento. */
    @Column(name = "referencia")
    private Long referencia;

    @Column(name = "estado", nullable = false)
    private Boolean estado = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
