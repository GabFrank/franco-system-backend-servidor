package com.franco.dev.service.rrhh;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

/**
 * Job diario que genera los bonos del mes corriente a partir de las plantillas
 * recurrentes activas.
 *
 * Corre todos los dias, no solo el dia 1: generarUno() es idempotente, asi que
 * las corridas siguientes son no-ops baratas. Eso hace que el job se auto-repare
 * — si el backend estuvo caido o desplegando el dia 1, el del dia 2 genera igual,
 * con fecha del dia 1, y el bono entra a la liquidacion del mes correcto.
 *
 * El bucle vive aca y no dentro del service para que cada llamada a generarUno()
 * cruce el proxy de Spring y tenga su propia transaccion: una plantilla que falla
 * no arrastra a las demas.
 *
 * 06:30 porque PrestamoCuotaScheduler ya ocupa las 06:00 y PenalizacionScheduler
 * las 05:00.
 */
@Service
@AllArgsConstructor
public class BonoRecurrenteScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BonoRecurrenteScheduler.class);

    private final BonoRecurrenteService service;

    @Scheduled(cron = "${rrhh.bono.recurrente.cron:0 30 6 * * ?}")
    public void generarBonosDelMes() {
        generarPeriodo(YearMonth.now());
    }

    /** Separado de generarBonosDelMes() para poder testear sin depender del reloj. */
    public int generarPeriodo(YearMonth periodo) {
        int generados = 0;
        for (Long id : service.plantillasActivas()) {
            try {
                if (service.generarUno(id, periodo).isPresent()) generados++;
            } catch (Exception e) {
                LOGGER.error("BonoRecurrenteScheduler: error en plantilla {} periodo {}", id, periodo, e);
            }
        }
        if (generados > 0) {
            LOGGER.info("BonoRecurrenteScheduler: {} bonos generados para {}", generados, periodo);
        }
        return generados;
    }
}
