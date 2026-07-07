package com.franco.dev.service.rrhh;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Job diario que genera las penalizaciones automaticas por tardanza sobre las
 * jornadas del dia anterior (N-1). Se corre sobre N-1, no sobre el dia
 * corriente, para dar margen al lag de replicacion de la marcacion
 * (BRANCH_TO_MAIN) hacia el central.
 */
@Service
@AllArgsConstructor
public class PenalizacionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PenalizacionScheduler.class);

    private final PenalizacionService penalizacionService;

    // Todos los dias a las 05:00 (hora del servidor).
    @Scheduled(cron = "${rrhh.penalizacion.cron:0 0 5 * * ?}")
    public void generarPenalizacionesDiaAnterior() {
        LocalDate fecha = LocalDate.now().minusDays(1);
        try {
            int generadas = penalizacionService.generarPenalizacionesAuto(fecha);
            if (generadas > 0) {
                LOGGER.info("PenalizacionScheduler: {} penalizaciones generadas para {}", generadas, fecha);
            }
        } catch (Exception e) {
            LOGGER.error("PenalizacionScheduler: error generando penalizaciones para {}", fecha, e);
        }
    }
}
