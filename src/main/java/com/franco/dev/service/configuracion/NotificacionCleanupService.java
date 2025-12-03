package com.franco.dev.service.configuracion;

import com.franco.dev.repository.configuracion.NotificacionRepository;
import com.franco.dev.repository.configuracion.NotificacionUsuarioRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacionCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(NotificacionCleanupService.class);

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private NotificacionUsuarioRepository notificacionUsuarioRepository;

    @Value("${notificacion.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${notificacion.cleanup.retention-days:30}")
    private int retentionDays;

    @Value("${notificacion.cleanup.cron:0 0 2 * * ?}")
    private String cron;

    @Scheduled(cron = "${notificacion.cleanup.cron:0 0 2 * * ?}")
    @Transactional
    public void limpiarNotificacionesAntiguas() {
        if (!cleanupEnabled) {
            return;
        }

        LocalDateTime fechaLimite = LocalDateTime.now().minusDays(retentionDays);

        try {
            notificacionUsuarioRepository.deleteByFechaEnvioBefore(fechaLimite);
        } catch (Exception e) {
            // Silent cleanup
        }

        try {
            notificacionRepository.deleteByCreadoEnBefore(fechaLimite);
        } catch (Exception e) {
            // Silent cleanup
        }
    }
}
