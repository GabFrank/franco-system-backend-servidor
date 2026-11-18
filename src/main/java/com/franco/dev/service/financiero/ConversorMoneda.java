package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Convierte un monto entre dos monedas con la cotizacion del comercio a un instante. Pasa por el
 * guarani, que es en lo que esta expresada toda cotizacion: real -> dolar = x Gs/BRL / Gs/USD.
 * <p>
 * Reglas: aritmetica en BigDecimal sin redondear en el medio, y un solo redondeo al final, a los
 * decimales de la moneda destino (HALF_UP). Si falta la cotizacion de alguna de las dos devuelve
 * vacio: inventar una (la ultima conocida, 1) es justamente lo que se quiere dejar de hacer.
 */
@Service
@AllArgsConstructor
public class ConversorMoneda {

    private static final MathContext CALCULO = MathContext.DECIMAL128;

    private final CotizacionService cotizacionService;

    public Optional<BigDecimal> convertir(BigDecimal monto, Moneda de, Moneda a, LocalDateTime instante) {
        return convertirSinRedondear(monto, de, a, instante)
                .map(v -> v.setScale(MonedaService.decimales(a), RoundingMode.HALF_UP));
    }

    /** Lo mismo, sin el redondeo final: para quien sigue calculando con el resultado. */
    public Optional<BigDecimal> convertirSinRedondear(BigDecimal monto, Moneda de, Moneda a, LocalDateTime instante) {
        if (monto == null || de == null || a == null) return Optional.empty();
        if (MonedaService.misma(de, a)) return Optional.of(monto);
        Optional<BigDecimal> gsPorUnidadDe = cotizacionService.delComercio(de, instante);
        Optional<BigDecimal> gsPorUnidadA = cotizacionService.delComercio(a, instante);
        if (!gsPorUnidadDe.isPresent() || !gsPorUnidadA.isPresent()) return Optional.empty();
        return Optional.of(monto.multiply(gsPorUnidadDe.get(), CALCULO)
                .divide(gsPorUnidadA.get(), CALCULO));
    }
}
