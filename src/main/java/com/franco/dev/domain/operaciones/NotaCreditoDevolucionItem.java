package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

/**
 * Linea consolidada de una nota de credito de devolucion. Un producto por linea
 * (consolidado a traves de todas las devoluciones del retiro). Se guarda la
 * presentacion elegida por el usuario junto con la cantidad/costo en esa
 * presentacion, y ademas cantidadBase (canonico en unidad base) para calculos.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "nota_credito_devolucion_item", schema = "operaciones")
public class NotaCreditoDevolucionItem implements Identifiable<Long> {

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
    @JoinColumn(name = "nota_credito_id", nullable = false)
    private NotaCreditoDevolucion notaCredito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id")
    private Presentacion presentacion;

    /** Cantidad en unidades de la presentacion elegida. */
    @Column(name = "cantidad")
    private Double cantidad;

    /** Costo por unidad de la presentacion elegida. */
    @Column(name = "costo_unitario")
    private Double costoUnitario;

    /** Cantidad en unidad base del producto (canonico). */
    @Column(name = "cantidad_base")
    private Double cantidadBase;

    /** cantidad * costoUnitario (== cantidadBase * costoUnitarioBase). */
    @Column(name = "total")
    private Double total;
}
