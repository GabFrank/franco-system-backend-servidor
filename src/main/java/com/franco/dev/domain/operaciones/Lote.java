package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maestro de lotes. Un lote es una entidad propia, no un texto repetido en cada movimiento.
 *
 * La unicidad es {@code (producto, numeroLote)}: el número de lote lo asigna el fabricante por
 * producto, así que el mismo código puede repetirse entre productos distintos sin que sean el
 * mismo lote. Es el mismo criterio de {@code stock.lot} en Odoo y de batch a nivel material en SAP.
 *
 * Acá viven las fechas y el estado; el saldo disponible se deriva sumando el ledger
 * {@link MovimientoStockLote} (ver la vista {@code operaciones.v_stock_lote}).
 *
 * Replicación MAIN_TO_ALL: se administra solo en el central y baja completo a todas las filiales.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "lote", schema = "operaciones")
public class Lote implements Serializable, Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "numero_lote", nullable = false)
    private String numeroLote;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    /**
     * Fecha a partir de la cual la mercadería debe salir de stock. Es la que ordena FEFO, no el
     * vencimiento: se calcula restando {@code producto.diasVencimiento} al vencimiento, para sacar
     * el producto antes de que efectivamente venza.
     */
    @Column(name = "fecha_retiro")
    private LocalDate fechaRetiro;

    @Column(name = "fecha_fabricacion")
    private LocalDate fechaFabricacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoLote estado = EstadoLote.LIBERADO;

    @Column(name = "observacion")
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}
