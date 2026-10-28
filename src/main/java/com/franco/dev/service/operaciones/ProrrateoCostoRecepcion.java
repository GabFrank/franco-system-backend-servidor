package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.NotaRecepcion;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collection;
import java.util.Objects;

/**
 * Prorrateo de los costos adicionales de una recepcion (flete, despacho) entre sus items.
 * <p>
 * Todo se suma en guaranies: los items pueden venir de notas en monedas distintas y un costo
 * adicional puede estar en otra moneda que la nota. Antes se sumaban los montos crudos, asi que un
 * flete en Gs sobre una nota en USD se cargaba como si fueran dolares.
 * <p>
 * La conversion a Gs usa la misma regla que {@code CostoMedioCalculator.aGuaranies}: solo se
 * multiplica si la cotizacion es mayor a 1 (las notas en Gs llevan cotizacion 1). Asi el peso de
 * cada item en el prorrateo es el mismo valor en Gs que despues entra al costo medio.
 */
final class ProrrateoCostoRecepcion {

    private static final MathContext CALCULO = MathContext.DECIMAL128;

    private ProrrateoCostoRecepcion() {
    }

    /** Guaranies por unidad de la moneda de una nota con esta cotizacion. */
    static BigDecimal factorGs(Double cotizacion) {
        return cotizacion != null && cotizacion > 1.0 ? BigDecimal.valueOf(cotizacion) : BigDecimal.ONE;
    }

    /**
     * Guaranies por unidad de la moneda de un costo adicional: la cotizacion de una nota de la
     * recepcion en esa moneda, si no la de la recepcion, y si es el guarani, 1. Si ninguna nota ni la
     * recepcion estan en esa moneda no hay cotizacion que usar, y no se inventa una.
     */
    static BigDecimal gsPorUnidad(Moneda moneda, Collection<NotaRecepcion> notas,
                                  Moneda monedaRecepcion, Double cotizacionRecepcion, String descripcion) {
        if (moneda == null || moneda.getId() == null) {
            throw new IllegalStateException("El costo adicional '" + descripcion + "' no tiene moneda.");
        }
        for (NotaRecepcion nota : notas) {
            if (nota.getMoneda() != null && Objects.equals(nota.getMoneda().getId(), moneda.getId())) {
                return factorGs(nota.getCotizacion());
            }
        }
        if (monedaRecepcion != null && Objects.equals(monedaRecepcion.getId(), moneda.getId())) {
            return factorGs(cotizacionRecepcion);
        }
        if ("GUARANI".equals(moneda.getDenominacion())) {
            return BigDecimal.ONE;
        }
        throw new IllegalStateException("El costo adicional '" + descripcion + "' esta en "
                + moneda.getDenominacion() + " y ninguna nota de la recepcion tiene cotizacion de esa moneda.");
    }

    /**
     * Costo unitario en la moneda de la nota: el precio mas su parte de los adicionales. La parte es
     * proporcional al valor del item, asi que el recargo es el mismo para todos:
     * {@code precio * adicionalesGs / valorItemsGs} (la cotizacion de la nota se cancela). Sin
     * adicionales devuelve el precio tal cual, sin aritmetica: es el caso de toda recepcion de hoy.
     * No se redondea: es un intermedio que entra al costo medio.
     */
    static double costoUnitario(double precioNota, BigDecimal adicionalesGs, BigDecimal valorItemsGs) {
        if (adicionalesGs.signum() <= 0 || valorItemsGs.signum() <= 0) {
            return precioNota;
        }
        BigDecimal precio = BigDecimal.valueOf(precioNota);
        return precio.add(precio.multiply(adicionalesGs, CALCULO).divide(valorItemsGs, CALCULO)).doubleValue();
    }
}
