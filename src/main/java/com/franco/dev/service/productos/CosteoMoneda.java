package com.franco.dev.service.productos;

import com.franco.dev.domain.financiero.ConfiguracionMonedaModulo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.service.financiero.CotizacionService;
import com.franco.dev.service.financiero.MonedaModuloLector;
import com.franco.dev.service.financiero.MonedaService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * El costo de un producto en la moneda de costeo (modulo COSTOS). Las columnas de siempre de
 * {@code costo_por_producto} siguen en guaranies, la moneda de la empresa; esta clase llena las
 * {@code *_costeo} de la fila nueva, ponderando en la moneda de costeo y no en guaranies: ponderar
 * en Gs y convertir despues revaluaria todo el stock a la cotizacion del dia.
 * <p>
 * Reglas:
 * <ul>
 *   <li>Costeo = moneda de la empresa (sin configurar): las columnas de costeo son copia de las de
 *       siempre, sin redondeo.</li>
 *   <li>Otra moneda: el precio de la compra en su moneda si la nota esta en la de costeo; si no, el
 *       de la empresa dividido por la cotizacion. La aritmetica es BigDecimal y se redondea una sola
 *       vez, a los decimales de la moneda de costeo.</li>
 *   <li>Punto de partida (ver {@link #puntoDePartida}): sin UPDATE masivo, la fila anterior sin costo
 *       en esta moneda aporta su costo de la empresa convertido a la cotizacion de compras de
 *       {@code vigente_desde}.</li>
 *   <li>Nunca lanza: sin cotizacion, la fila queda con las columnas de costeo en NULL y un log. El
 *       costo de siempre se guarda igual.</li>
 * </ul>
 */
@Service
@AllArgsConstructor
public class CosteoMoneda {

    private static final Logger log = LoggerFactory.getLogger(CosteoMoneda.class);
    private static final MathContext CALCULO = MathContext.DECIMAL128;

    private final MonedaModuloLector monedaModuloLector;
    private final CotizacionService cotizacionService;

    /**
     * Completa las columnas de costeo de {@code nuevo}, que ya trae las de la empresa.
     *
     * @param anterior        la ultima fila del producto (puede ser null)
     * @param monedaCompra    moneda de la nota (null en la via manual)
     * @param precioCompra    precio unitario en {@code monedaCompra}, sin convertir (null en la via manual)
     * @param cotizCompra     guaranies por unidad de {@code monedaCompra} que uso la compra
     * @param stockReal       stock real con la entrada incluida; null = no pondera (via manual)
     * @param cantidad        cantidad de la entrada
     */
    public void completar(CostoPorProducto nuevo, CostoPorProducto anterior, Moneda monedaCompra,
                          Double precioCompra, double cotizCompra, Double stockReal, double cantidad,
                          LocalDateTime fecha) {
        try {
            Moneda costeo = monedaModuloLector.resolver(ModuloMoneda.COSTOS, null, fecha);
            if (costeo == null || costeo.getId() == null) return;
            nuevo.setMonedaCosteo(costeo);
            if (esLaDeLaEmpresa(costeo)) {
                nuevo.setCotizacionCosteo(BigDecimal.ONE);
                nuevo.setUltimoPrecioCompraCosteo(decimal(nuevo.getUltimoPrecioCompra()));
                nuevo.setCostoMedioCosteo(decimal(nuevo.getCostoMedio()));
                return;
            }
            boolean enSuMoneda = MonedaService.misma(costeo, monedaCompra) && precioCompra != null;
            BigDecimal gsPorUnidad = enSuMoneda && cotizCompra > 1
                    ? BigDecimal.valueOf(cotizCompra)
                    : cotizacionService.paraCompra(costeo, fecha).orElse(null);
            if (gsPorUnidad == null || gsPorUnidad.signum() <= 0) {
                log.warn("COSTEO_SIN_COTIZACION producto={} moneda={}: la fila queda sin costo de costeo",
                        id(nuevo), costeo.getDenominacion());
                nuevo.setMonedaCosteo(null);
                return;
            }
            BigDecimal precio = enSuMoneda
                    ? BigDecimal.valueOf(precioCompra)
                    : BigDecimal.valueOf(nuevo.getUltimoPrecioCompra()).divide(gsPorUnidad, CALCULO);
            BigDecimal medio = precio;
            if (stockReal != null) {
                BigDecimal partida = enMonedaDeCosteo(anterior, costeo,
                        anterior != null ? anterior.getCostoMedioCosteo() : null,
                        anterior != null ? anterior.getCostoMedio() : null, fecha, false).orElse(null);
                BigDecimal stockAnterior = BigDecimal.valueOf(stockReal).subtract(BigDecimal.valueOf(cantidad));
                // La misma regla que CostoMedioCalculator.calcular: sin base valida no se pondera.
                if (stockAnterior.signum() > 0 && partida != null && partida.signum() > 0 && precio.signum() > 0) {
                    medio = stockAnterior.multiply(partida, CALCULO)
                            .add(BigDecimal.valueOf(cantidad).multiply(precio, CALCULO), CALCULO)
                            .divide(BigDecimal.valueOf(stockReal), CALCULO);
                }
            }
            int decimales = MonedaService.decimales(costeo);
            nuevo.setCotizacionCosteo(gsPorUnidad);
            nuevo.setUltimoPrecioCompraCosteo(precio.setScale(decimales, RoundingMode.HALF_UP));
            nuevo.setCostoMedioCosteo(medio.setScale(decimales, RoundingMode.HALF_UP));
        } catch (Exception e) {
            log.warn("COSTEO_ILEGIBLE producto={}: la fila queda sin costo de costeo", id(nuevo), e);
            nuevo.setMonedaCosteo(null);
            nuevo.setCotizacionCosteo(null);
            nuevo.setUltimoPrecioCompraCosteo(null);
            nuevo.setCostoMedioCosteo(null);
        }
    }

    /**
     * El costo medio de {@code fila} en la moneda {@code costeo}: el guardado si la fila ya esta en
     * esa moneda; si no, su costo de la empresa a la cotizacion de compras de {@code vigente_desde}
     * de la configuracion de COSTOS que rige ahora (sin fila, la de ahora). Vacio si no hay de donde
     * sacarlo. Redondeado a los decimales de la moneda: es para mostrar.
     */
    public Optional<BigDecimal> puntoDePartida(CostoPorProducto fila, Moneda costeo) {
        return enMonedaDeCosteo(fila, costeo, fila != null ? fila.getCostoMedioCosteo() : null,
                fila != null ? fila.getCostoMedio() : null, null, true);
    }

    /** Lo mismo para el ultimo precio de compra. */
    public Optional<BigDecimal> ultimoPrecioDePartida(CostoPorProducto fila, Moneda costeo) {
        return enMonedaDeCosteo(fila, costeo, fila != null ? fila.getUltimoPrecioCompraCosteo() : null,
                fila != null ? fila.getUltimoPrecioCompra() : null, null, true);
    }

    /** La moneda de costeo de ahora. */
    public Moneda monedaDeCosteo() {
        return monedaModuloLector.resolver(ModuloMoneda.COSTOS, null);
    }

    /** {@code redondear=false} para quien sigue calculando: el redondeo es uno solo, al final. */
    private Optional<BigDecimal> enMonedaDeCosteo(CostoPorProducto fila, Moneda costeo, BigDecimal guardado,
                                                  Double enGuaranies, LocalDateTime cuando, boolean redondear) {
        if (fila == null || costeo == null) return Optional.empty();
        if (guardado != null && MonedaService.misma(fila.getMonedaCosteo(), costeo)) return Optional.of(guardado);
        if (enGuaranies == null) return Optional.empty();
        BigDecimal gs = BigDecimal.valueOf(enGuaranies);
        if (esLaDeLaEmpresa(costeo)) return Optional.of(gs);
        LocalDateTime desde = monedaModuloLector.filaGlobal(ModuloMoneda.COSTOS, cuando)
                .filter(f -> MonedaService.misma(f.getMoneda(), costeo))
                .map(ConfiguracionMonedaModulo::getVigenteDesde)
                .orElse(null);
        return cotizacionService.paraCompra(costeo, desde)
                .filter(c -> c.signum() > 0)
                .map(c -> gs.divide(c, CALCULO))
                .map(v -> redondear ? v.setScale(MonedaService.decimales(costeo), RoundingMode.HALF_UP) : v);
    }

    /**
     * Las columnas de siempre estan en la moneda de la empresa (el guarani): si la de costeo es esa,
     * las de costeo son copia. Por id contra la de la empresa, y por las dudas tambien el guarani.
     */
    private boolean esLaDeLaEmpresa(Moneda costeo) {
        return MonedaService.esGuarani(costeo) || MonedaService.misma(costeo, monedaModuloLector.empresa());
    }

    private static BigDecimal decimal(Double v) {
        return v != null ? BigDecimal.valueOf(v) : null;
    }

    private static Long id(CostoPorProducto c) {
        return c.getProducto() != null ? c.getProducto().getId() : null;
    }
}
