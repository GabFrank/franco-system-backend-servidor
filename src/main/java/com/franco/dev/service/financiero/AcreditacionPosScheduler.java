package com.franco.dev.service.financiero;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Acredita automáticamente las acreditaciones POS pendientes vencidas. Desactivado
 * por defecto; se habilita por instancia. El posteo es idempotente por estado
 * (cada acreditación se relee y solo se procesa si sigue PENDIENTE), por lo que
 * dos ticks solapados no duplican (AJ-17).
 */
@Component
@ConditionalOnProperty(name = "tesoreria.acreditacion-pos.enabled", havingValue = "true", matchIfMissing = false)
public class AcreditacionPosScheduler {

    private static final Logger log = LoggerFactory.getLogger(AcreditacionPosScheduler.class);
    private static final int LOTE = 100;

    private final AcreditacionPosService service;

    public AcreditacionPosScheduler(AcreditacionPosService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${tesoreria.acreditacion-pos.fixed-delay:300000}",
            initialDelayString = "${tesoreria.acreditacion-pos.initial-delay:60000}"
    )
    public void procesar() {
        try {
            int n = service.procesarPendientes(LOTE);
            if (n > 0) log.info("AcreditacionPosScheduler: {} acreditaciones POS procesadas", n);
        } catch (Exception e) {
            log.warn("AcreditacionPosScheduler: error: {}", e.getMessage());
        }
    }
}
