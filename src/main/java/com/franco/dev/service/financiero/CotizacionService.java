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
 * del PDV. La cotizacion de compras (mercado primero) se agrega en la entrega de compras.
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
}
