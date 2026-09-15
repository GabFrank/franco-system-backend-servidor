package com.franco.dev.service.financiero;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Borra las fotos de muestra viejas.
 *
 * <p>Una foto de cupon son ~200 KB y se genera una por cada derivacion, asi que sin purga esto
 * crece sin techo en el disco de central.
 *
 * <p><b>Encendido por default, al reves que los schedulers de tesoreria.</b> Aquellos mueven plata
 * y por eso arrancan apagados: que no corran no rompe nada. Este es lo contrario --lo que rompe es
 * que NO corra-- y apagado por omision seria una fuga de disco silenciosa en cada instancia que
 * nadie configure. Se apaga con {@code frc.captura-muestra.purga.enabled=false}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "frc.captura-muestra.purga.enabled", havingValue = "true",
        matchIfMissing = true)
public class CapturaMuestraPurgaScheduler {

    private final CapturaMuestraService service;

    /**
     * Cuanto se conservan. Seis meses: cubre de sobra volver a mirar el cupon que produjo un mapa,
     * y deja juntar el corpus de la etapa 6 con varios modelos de aparato.
     */
    @Value("${frc.captura-muestra.dias-retencion:180}")
    private int diasRetencion;

    public CapturaMuestraPurgaScheduler(CapturaMuestraService service) {
        this.service = service;
    }

    /** A las 3 de la mañana: no compite con nada y las cajas estan cerradas. */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgar() {
        try {
            int borradas = service.purgarAnterioresA(LocalDateTime.now().minusDays(diasRetencion));
            if (borradas > 0) {
                log.info("purga de muestras: {} borradas (retencion {} dias)", borradas, diasRetencion);
            }
        } catch (Exception e) {
            log.error("fallo la purga de muestras", e);
        }
    }
}
