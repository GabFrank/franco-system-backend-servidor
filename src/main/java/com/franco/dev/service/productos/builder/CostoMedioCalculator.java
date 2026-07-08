package com.franco.dev.service.productos.builder;

import java.util.Objects;

/**
 * Lógica pura (sin estado, sin DB) del cálculo de costo medio de un producto.
 *
 * Reglas:
 * - El costo medio es GLOBAL por producto y se calcula en Gs (moneda base).
 * - Solo se pondera (promedio ponderado clásico) cuando hay base válida: stock anterior real > 0,
 *   costo medio anterior > 0 y costo de la compra > 0. En cualquier otro caso (stock negativo o cero,
 *   sin costo previo válido) NO hay forma de ponderar y el costo medio se resetea al costo de la compra.
 * - El "stock real" excluye la pseudo-sucursal COMPRAS (cuenta de clearing), pero eso lo resuelve el caller.
 */
public final class CostoMedioCalculator {

    /** Tolerancia para comparar costos (Gs no usa decimales; la ponderación puede generar ruido). */
    public static final double TOLERANCIA = 0.01;

    private CostoMedioCalculator() {
    }

    /**
     * Normaliza el costo unitario a Gs. Igual que el sistema: solo convierte si la cotización es > 1
     * (las notas en Gs usan cotización 1, las extranjeras una cotización mayor).
     */
    public static double aGuaranies(double costoUnitario, double cotizacion) {
        return (cotizacion > 1.0) ? costoUnitario * cotizacion : costoUnitario;
    }

    /**
     * Calcula el costo medio resultante (en Gs).
     *
     * @param stockReal          inventario real del producto LUEGO de la entrada de esta compra (denominador)
     * @param cantidadEntrada    cantidad comprada en esta operación (&gt; 0)
     * @param costoMedioAnterior costo medio previo en Gs (puede ser null)
     * @param costoEnGs          costo de esta compra en Gs (&gt; 0)
     * @return costo medio ponderado si hay base válida; si no, {@code costoEnGs}
     */
    public static double calcular(double stockReal, double cantidadEntrada, Double costoMedioAnterior,
                                  double costoEnGs) {
        double stockAnterior = stockReal - cantidadEntrada;
        if (stockAnterior > 0 && costoMedioAnterior != null && costoMedioAnterior > 0) {
            return (stockAnterior * costoMedioAnterior + cantidadEntrada * costoEnGs) / stockReal;
        }
        // Stock negativo/cero o sin costo previo válido: no se puede ponderar.
        return costoEnGs;
    }

    /**
     * Decide si el costo cambió respecto al último registro (dedup). Devuelve true si hay que insertar
     * una fila nueva. Compara costoMedio y ultimoPrecioCompra con tolerancia, y la moneda por id.
     */
    public static boolean cambio(Double costoMedioAnterior, Double ultimoPrecioCompraAnterior, Long monedaIdAnterior,
                                 double costoMedioNuevo, double ultimoPrecioCompraNuevo, Long monedaIdNuevo) {
        if (costoMedioAnterior == null || ultimoPrecioCompraAnterior == null) {
            return true;
        }
        if (Math.abs(costoMedioAnterior - costoMedioNuevo) >= TOLERANCIA) {
            return true;
        }
        if (Math.abs(ultimoPrecioCompraAnterior - ultimoPrecioCompraNuevo) >= TOLERANCIA) {
            return true;
        }
        return !Objects.equals(monedaIdAnterior, monedaIdNuevo);
    }
}
