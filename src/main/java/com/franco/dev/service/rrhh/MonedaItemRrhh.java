package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.service.financiero.ConversorMoneda;
import com.franco.dev.service.financiero.MonedaService;
import graphql.GraphQLException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Pasa los montos de los items de un documento RRHH (liquidacion o finiquito) a la moneda del documento.
 * Uno por documento: cada cotizacion se lee una sola vez y todos los items usan la misma.
 * <p>
 * Un vale o una cuota en otra moneda entraba con su numero crudo (R$ 110 descontaba 110 Gs). Sin
 * cotizacion no se convierte con 1: se lanza {@link CotizacionFaltante}.
 */
public final class MonedaItemRrhh {

    /** Falta la cotizacion de una moneda que hay que convertir. */
    public static class CotizacionFaltante extends GraphQLException {
        public CotizacionFaltante(String message) {
            super(message);
        }
    }

    /** Un monto ya cotizado: moneda y monto del origen, factor y monto en la moneda del documento. */
    public static final class Cotizado {
        public final Moneda moneda;
        public final BigDecimal montoMoneda;
        public final BigDecimal cotizacion;
        public final BigDecimal monto;

        Cotizado(Moneda moneda, BigDecimal montoMoneda, BigDecimal cotizacion, BigDecimal monto) {
            this.moneda = moneda;
            this.montoMoneda = montoMoneda;
            this.cotizacion = cotizacion;
            this.monto = monto;
        }
    }

    private final ConversorMoneda conversor;
    private final Moneda destino;
    private final LocalDateTime instante;
    private final Map<Long, BigDecimal> factores = new HashMap<>();

    /** {@code destino} null (sin moneda resuelta): no se convierte nada, como antes de esto. */
    public MonedaItemRrhh(ConversorMoneda conversor, Moneda destino, LocalDateTime instante) {
        this.conversor = conversor;
        this.destino = destino;
        this.instante = instante;
    }

    public Moneda getDestino() {
        return destino;
    }

    /** Factor origen → destino; 1 si el origen no tiene moneda o es la del documento. */
    public BigDecimal factor(Moneda origen) {
        if (origen == null || destino == null || MonedaService.misma(origen, destino)) return BigDecimal.ONE;
        return factores.computeIfAbsent(origen.getId(), id -> conversor
                .convertirSinRedondear(BigDecimal.ONE, origen, destino, instante)
                .orElseThrow(() -> new CotizacionFaltante("No hay cotizacion cargada para pasar de "
                        + origen.getDenominacion() + " a " + destino.getDenominacion())));
    }

    /** {@code montoMoneda} en {@code origen} (null = la del documento), pasado a la moneda del documento. */
    public Cotizado cotizar(BigDecimal montoMoneda, Moneda origen, RoundingMode modo) {
        Moneda o = origen != null ? origen : destino;
        BigDecimal m = montoMoneda != null ? montoMoneda : BigDecimal.ZERO;
        BigDecimal f = factor(o);
        // Misma moneda: el monto tal cual, sin re-escalar (el IPS en Gs lleva 2 decimales desde siempre).
        BigDecimal monto = f.compareTo(BigDecimal.ONE) == 0 ? m
                : m.multiply(f).setScale(MonedaService.decimales(destino), modo);
        return new Cotizado(o, m, f, monto);
    }

    /** Un monto de la moneda del documento, pasado a guaranies hacia abajo (tope del convenio). */
    public BigDecimal aGuaranies(BigDecimal monto, Moneda guarani) {
        BigDecimal f = factor(guarani);
        if (monto == null || f.compareTo(BigDecimal.ONE) == 0) return monto;
        return monto.divide(f, 0, RoundingMode.DOWN);
    }

    /** De la moneda del documento a la del origen: la edicion manual de un item convertido. */
    public static BigDecimal aOrigen(BigDecimal monto, BigDecimal cotizacion, Moneda origen) {
        if (monto == null || cotizacion == null || cotizacion.compareTo(BigDecimal.ONE) == 0) return monto;
        return monto.divide(cotizacion, MonedaService.decimales(origen), RoundingMode.HALF_UP);
    }
}
