package com.franco.dev.service.operaciones;

import com.franco.dev.domain.productos.Presentacion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversion entre unidades y presentaciones para el stock por lote.
 *
 * Existe como clase aparte, y no como metodos sueltos en cada servicio, porque la MISMA regla se
 * aplica en dos momentos distintos: al mostrar el saldo disponible de un lote y al persistir la
 * cantidad que el operador eligio. Si cada lado hiciera su propia division, un lote de 89 unidades
 * en cajas de 6 podria mostrarse como 14,833 y guardarse como 14,834, y el desglose por lote
 * dejaria de cuadrar con el movimiento de stock.
 */
public final class ConversionPresentacion {

    /**
     * Decimales con los que se corta la division. Seis alcanza de sobra para cualquier
     * presentacion real y evita arrastrar la cola binaria de un double.
     */
    private static final int ESCALA = 6;

    private ConversionPresentacion() {
    }

    /**
     * Unidades que vale una presentacion. Devuelve 1 cuando no hay presentacion o su cantidad no
     * es utilizable, con lo que unidades y presentaciones pasan a ser lo mismo.
     */
    public static double unidadesPorPresentacion(Presentacion presentacion) {
        if (presentacion == null || presentacion.getCantidad() == null
                || presentacion.getCantidad() <= 0) {
            return 1d;
        }
        return presentacion.getCantidad();
    }

    /**
     * Pasa una cantidad en unidades a la presentacion dada, sin redondear.
     *
     * Se usa para reexpresar una cantidad YA elegida, que siempre es un multiplo exacto de la
     * presentacion. Para el saldo disponible NO sirve: ver {@link #presentacionesCompletas}.
     */
    public static double aPresentaciones(Double unidades, double unidadesPorPresentacion) {
        if (unidades == null || unidades == 0d) {
            return 0d;
        }
        return redondear(unidades / normalizar(unidadesPorPresentacion));
    }

    /**
     * Presentaciones COMPLETAS que entran en una cantidad de unidades.
     *
     * Una presentacion es indivisible: de un lote de 65 unidades con cajas de 6 se pueden sacar
     * 10 cajas, no 10,833. Las 5 unidades que sobran existen en el stock pero no se pueden mover
     * con esa presentacion; salen con una presentacion de menor tamano.
     */
    public static double presentacionesCompletas(Double unidades, double unidadesPorPresentacion) {
        if (unidades == null || unidades <= 0d) {
            return 0d;
        }
        return Math.floor(unidades / normalizar(unidadesPorPresentacion));
    }

    /** Unidades que quedan fuera de las presentaciones completas. */
    public static double unidadesSobrantes(Double unidades, double unidadesPorPresentacion) {
        if (unidades == null || unidades <= 0d) {
            return 0d;
        }
        double factor = normalizar(unidadesPorPresentacion);
        return redondear(unidades - presentacionesCompletas(unidades, factor) * factor);
    }

    /** Pasa una cantidad expresada en presentaciones a unidades, que es como vive el ledger. */
    public static double aUnidades(Double presentaciones, double unidadesPorPresentacion) {
        if (presentaciones == null || presentaciones == 0d) {
            return 0d;
        }
        return redondear(presentaciones * normalizar(unidadesPorPresentacion));
    }

    private static double normalizar(double unidadesPorPresentacion) {
        return unidadesPorPresentacion > 0 ? unidadesPorPresentacion : 1d;
    }

    private static double redondear(double valor) {
        return BigDecimal.valueOf(valor)
                .setScale(ESCALA, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
