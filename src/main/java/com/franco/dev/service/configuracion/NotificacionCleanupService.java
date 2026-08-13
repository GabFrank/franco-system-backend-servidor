package com.franco.dev.service.configuracion;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vacia por completo el modulo de notificaciones: cada corrida borra todo el
 * contenido, no una ventana de retencion.
 *
 * Corre cada 7 dias y ademas al arrancar el servidor. El arranque importa
 * porque un cron a hora fija no dispara nunca en los entornos donde el server
 * no esta levantado a esa hora, que es como notificacion llego a acumular 80
 * dias en dev teniendo 30 configurados.
 *
 * Se vacian las cuatro tablas del modulo. notificacion_comentario cuelga de
 * notificacion con ON DELETE CASCADE igual que envio_log y destinatario, asi
 * que se va con ellas inevitablemente.
 */
@Service
public class NotificacionCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(NotificacionCleanupService.class);

    private static final long UNA_SEMANA_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * Se listan las tablas explicitamente en vez de usar CASCADE: si algun dia
     * cuelga una tabla nueva de notificacion, esto falla y queda en el log en
     * lugar de vaciarla en silencio.
     */
    private static final String VACIAR_TABLAS = "TRUNCATE TABLE "
            + "configuraciones.notificacion_envio_log, "
            + "configuraciones.notificacion_destinatario, "
            + "configuraciones.notificacion_comentario, "
            + "configuraciones.notificacion";

    private static final String CONTAR_NOTIFICACIONES = "SELECT count(*) FROM configuraciones.notificacion";

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${notificacion.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${notificacion.cleanup.on-startup:true}")
    private boolean limpiarAlArrancar;

    /**
     * initialDelay igual al intervalo a proposito: sin el, la primera corrida
     * caeria junto con la del arranque y vaciaria dos veces seguidas.
     */
    @Scheduled(fixedRateString = "${notificacion.cleanup.interval:604800000}",
            initialDelayString = "${notificacion.cleanup.interval:604800000}")
    @Transactional
    public void limpiezaProgramada() {
        vaciar("programada");
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void limpiezaAlArrancar() {
        if (!limpiarAlArrancar) {
            logger.info("Limpieza de notificaciones al arranque deshabilitada");
            return;
        }
        vaciar("arranque");
    }

    private void vaciar(String origen) {
        if (!cleanupEnabled) {
            logger.info("Limpieza de notificaciones deshabilitada, se omite la corrida {}", origen);
            return;
        }
        try {
            Number notificaciones = (Number) entityManager
                    .createNativeQuery(CONTAR_NOTIFICACIONES)
                    .getSingleResult();
            entityManager.createNativeQuery(VACIAR_TABLAS).executeUpdate();
            logger.info("Limpieza de notificaciones ({}): se vaciaron {} notificaciones con sus destinatarios,"
                    + " logs de envio y comentarios", origen, notificaciones);
        } catch (Exception e) {
            // Nunca en silencio: si esto falla, las tablas crecen sin techo y
            // antes nadie se enteraba.
            logger.error("Fallo la limpieza de notificaciones ({})", origen, e);
        }
    }

    long intervaloPorDefectoMs() {
        return UNA_SEMANA_MS;
    }
}
