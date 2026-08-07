package com.franco.dev.service.rrhh;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Job diario que marca como VENCIDA las cuotas de prestamo cuya fecha de
 * vencimiento ya paso y siguen pendientes/parciales.
 */
@Service
@AllArgsConstructor
public class PrestamoCuotaScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrestamoCuotaScheduler.class);

    private final PrestamoService prestamoService;

    // Todos los dias a las 06:00 (hora del servidor).
    @Scheduled(cron = "${rrhh.prestamo.cuota.cron:0 0 6 * * ?}")
    public void marcarCuotasVencidas() {
        try {
            int n = prestamoService.marcarVencidas(LocalDate.now());
            if (n > 0) {
                LOGGER.info("PrestamoCuotaScheduler: {} cuotas marcadas como VENCIDA", n);
            }
        } catch (Exception e) {
            LOGGER.error("PrestamoCuotaScheduler: error marcando cuotas vencidas", e);
        }
    }
}
