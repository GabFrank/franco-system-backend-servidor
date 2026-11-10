package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.repository.financiero.CambioRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Cotizacion de una moneda en guaranies a un instante dado. Todas las cotizaciones de
 * {@code financiero.cambio} estan expresadas asi ("1 USD = 5.700 Gs"), sea cual sea la moneda de
 * la empresa: por eso el guarani es el pivote de {@link ConversorMoneda}.
 * <p>
 * {@link #delComercio} lee solo {@code valor_en_gs}, la que carga el comercio. Nunca las columnas
 * de mercado, que escribe {@code CotizacionMercadoScheduler} desde una fuente externa: es la regla
 * del PDV. {@link #paraCompra} es la de compras: mercado primero.
 */
@Service
@AllArgsConstructor
public class CotizacionService {

    private final CambioRepository cambioRepository;

    /**
     * Guaranies por una unidad de {@code moneda} segun la ultima cotizacion cargada por el comercio
     * hasta {@code instante}. El guarani vale 1. Vacio si no hay ninguna: quien llama decide.
     */
    public Optional<BigDecimal> delComercio(Moneda moneda, LocalDateTime instante) {
        if (moneda == null || moneda.getId() == null) return Optional.empty();
        if (MonedaService.esGuarani(moneda)) return Optional.of(BigDecimal.ONE);
        Cambio c = cambioRepository.findVigenteDelComercio(moneda.getId(),
                instante != null ? instante : LocalDateTime.now());
        if (c == null || c.getValorEnGs() == null || c.getValorEnGs() <= 0) return Optional.empty();
        // valueOf(double) usa la representacion corta del double: 5700.0 -> 5700.0, no 5699.99...
        return Optional.of(BigDecimal.valueOf(c.getValorEnGs()));
    }

    /**
     * Guaranies por una unidad de {@code moneda} para compras: de la ultima fila de cambio, el
     * mercado de compra, si no el de venta, si no {@code valor_en_gs}. Es el orden que tenian copiado
     * {@code RecepcionDocumentalService} y {@code ComprasMobileService}. Sin {@code instante}, la
     * ultima fila cargada (lo de siempre); con uno, la ultima hasta ese instante. El guarani vale 1.
     * Vacio si no hay fila o el valor no es positivo; el umbral propio (algunos piden &gt; 1) lo
     * aplica quien llama.
     * <p>
     * ⚠️ No reconstruye el pasado: {@code CotizacionMercadoScheduler} pisa cada 10 minutos el mercado de
     * la ultima fila, asi que con un instante anterior a la ultima carga del comercio el mercado que
     * se lee es el del dia de esa carga, no el del instante. Lo que necesita una cotizacion fija la
     * guarda al momento (cada documento la suya; el costo, {@code cotizacion_partida}).
     */
    public Optional<BigDecimal> paraCompra(Moneda moneda, LocalDateTime instante) {
        if (moneda == null || moneda.getId() == null) return Optional.empty();
        if (MonedaService.esGuarani(moneda)) return Optional.of(BigDecimal.ONE);
        Cambio c = instante == null
                ? cambioRepository.findLastByCambioId(moneda.getId())
                : cambioRepository.findUltimoHasta(moneda.getId(), instante);
        Double tasa = tasaDeCompra(c);
        if (tasa == null || !(tasa > 0)) return Optional.empty();
        return Optional.of(BigDecimal.valueOf(tasa));
    }

    /** Lo mismo, como Double, para el codigo de compras que sigue en Double. */
    public Double paraCompraDouble(Moneda moneda) {
        return paraCompra(moneda, null).map(BigDecimal::doubleValue).orElse(null);
    }

    static Double tasaDeCompra(Cambio c) {
        if (c == null) return null;
        if (c.getValorEnGsCompraMercado() != null) return c.getValorEnGsCompraMercado();
        if (c.getValorEnGsVentaMercado() != null) return c.getValorEnGsVentaMercado();
        return c.getValorEnGs();
    }
}
