package com.franco.dev.service.rrhh;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Job diario que marca prescritas las vacaciones cuyo periodo de goce ya
 * caduco segun la configuracion (ley PY).
 */
@Service
@AllArgsConstructor
public class VacacionPrescripcionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VacacionPrescripcionScheduler.class);

    private final VacacionService vacacionService;

    // Todos los dias a las 04:00 (hora del servidor).
    @Scheduled(cron = "${rrhh.vacacion.prescripcion.cron:0 0 4 * * ?}")
    public void prescribirVacaciones() {
        try {
            int n = vacacionService.prescribir();
            if (n > 0) {
                LOGGER.info("VacacionPrescripcionScheduler: {} vacaciones marcadas como prescritas", n);
            }
        } catch (Exception e) {
            LOGGER.error("VacacionPrescripcionScheduler: error al prescribir vacaciones", e);
        }
    }
}
