package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "cotizacion.mercado.enabled", havingValue = "true", matchIfMissing = false)
public class CotizacionMercadoScheduler {

    private static final Logger log = LoggerFactory.getLogger(CotizacionMercadoScheduler.class);

    private final NorteCambiosScraper scraper;
    private final CambioService cambioService;
    private final MonedaService monedaService;

    public CotizacionMercadoScheduler(NorteCambiosScraper scraper, CambioService cambioService, MonedaService monedaService) {
        this.scraper = scraper;
        this.cambioService = cambioService;
        this.monedaService = monedaService;
    }

    @Scheduled(
        fixedDelayString = "${cotizacion.mercado.fixed-delay:600000}",
        initialDelayString = "${cotizacion.mercado.initial-delay:60000}"
    )
    public void scheduledUpdate() {
        log.info("Actualizacion automatica de cotizaciones de mercado");
        try {
            int count = actualizarCotizaciones();
            log.info("CotizacionMercadoScheduler: {} monedas actualizadas", count);
        } catch (Exception e) {
            log.warn("CotizacionMercadoScheduler: error en actualizacion automatica: {}", e.getMessage());
        }
    }

    public int actualizarCotizaciones() {
        Map<String, double[]> rates = scraper.fetchRates();
        if (rates.isEmpty()) {
            throw new RuntimeException("No se pudieron obtener cotizaciones de nortecambios.com.py");
        }
        int count = 0;
        for (Map.Entry<String, double[]> entry : rates.entrySet()) {
            String monedaKey = entry.getKey();
            double[] rateValues = entry.getValue();
            try {
                Moneda moneda = monedaService.findByDescripcion(monedaKey);
                if (moneda == null) {
                    log.warn("CotizacionMercadoScheduler: moneda no encontrada: {}", monedaKey);
                    continue;
                }
                Cambio ultimo = cambioService.findLastByMonedaId(moneda.getId());
                if (ultimo == null) {
                    log.warn("CotizacionMercadoScheduler: no existe Cambio previo para {}", monedaKey);
                    continue;
                }
                ultimo.setValorEnGsVentaMercado(rateValues[0]);
                ultimo.setValorEnGsCompraMercado(rateValues[1]);
                cambioService.save(ultimo);
                count++;
            } catch (Exception e) {
                log.warn("CotizacionMercadoScheduler: error actualizando {}: {}", monedaKey, e.getMessage());
            }
        }
        return count;
    }
}
