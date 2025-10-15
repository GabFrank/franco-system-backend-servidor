package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.LoteDEService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de jobs programados para procesamiento automático de lotes SIFEN.
 * Implementa tareas programadas para:
 * - Enviar lotes pendientes
 * - Consultar lotes en proceso
 * - Reintentar lotes con errores
 */
@Slf4j
@Service
public class SifenJobService {

    @Autowired
    private LoteDEService loteDEService;

    @Autowired
    private SifenService sifenService;

    /**
     * Job programado para enviar lotes pendientes a SIFEN.
     * Se ejecuta cada 5 minutos.
     */
    @Scheduled(fixedRate = 300000) // Cada 5 minutos
    public void enviarLotesPendientes() {
        log.info("Ejecutando job: enviar lotes pendientes a SIFEN");

        try {
            List<LoteDE> lotesPendientes = loteDEService.findByEstado(EstadoLoteDE.PENDIENTE_ENVIO);

            if (lotesPendientes.isEmpty()) {
                log.debug("No hay lotes pendientes para enviar");
                return;
            }

            log.info("Procesando {} lotes pendientes", lotesPendientes.size());

            for (LoteDE lote : lotesPendientes) {
                try {
                    // Verificar que el lote no sea muy antiguo
                    if (lote.getCreadoEn().isBefore(LocalDateTime.now().minusHours(24))) {
                        log.warn("Lote ID {} tiene más de 24 horas, saltando", lote.getId());
                        continue;
                    }

                    log.info("Enviando lote pendiente ID: {}", lote.getId());
                    sifenService.enviarLote(lote);

                } catch (Exception e) {
                    log.error("Error al enviar lote ID {}: {}", lote.getId(), e.getMessage());
                    // Continuar con el siguiente lote
                }
            }

        } catch (Exception e) {
            log.error("Error en job de envío de lotes pendientes: {}", e.getMessage(), e);
        }
    }

    /**
     * Job programado para consultar lotes en proceso.
     * Se ejecuta cada 10 minutos.
     */
    @Scheduled(fixedRate = 600000) // Cada 10 minutos
    public void consultarLotesEnProceso() {
        log.info("Ejecutando job: consultar lotes en proceso");

        try {
            List<LoteDE> lotesEnProceso = loteDEService.findByEstado(EstadoLoteDE.EN_PROCESO);

            if (lotesEnProceso.isEmpty()) {
                log.debug("No hay lotes en proceso para consultar");
                return;
            }

            log.info("Consultando {} lotes en proceso", lotesEnProceso.size());

            for (LoteDE lote : lotesEnProceso) {
                try {
                    // Verificar que el lote no se haya consultado recientemente
                    if (lote.getFechaUltimoIntento() != null &&
                        lote.getFechaUltimoIntento().isAfter(LocalDateTime.now().minusMinutes(30))) {
                        log.debug("Lote ID {} consultado recientemente, saltando", lote.getId());
                        continue;
                    }

                    log.info("Consultando lote en proceso ID: {}", lote.getId());
                    sifenService.consultarLote(lote);

                } catch (Exception e) {
                    log.error("Error al consultar lote ID {}: {}", lote.getId(), e.getMessage());
                    // Continuar con el siguiente lote
                }
            }

        } catch (Exception e) {
            log.error("Error en job de consulta de lotes en proceso: {}", e.getMessage(), e);
        }
    }

    /**
     * Job programado para reintentar lotes con errores.
     * Se ejecuta cada 15 minutos.
     */
    @Scheduled(fixedRate = 900000) // Cada 15 minutos
    public void reintentarLotesConErrores() {
        log.info("Ejecutando job: reintentar lotes con errores");

        try {
            // Buscar lotes con errores que puedan ser reintentados
            List<LoteDE> lotesConErrores = loteDEService.findByEstado(EstadoLoteDE.ERROR_ENVIO);

            if (lotesConErrores.isEmpty()) {
                log.debug("No hay lotes con errores para reintentar");
                return;
            }

            log.info("Reintentando {} lotes con errores", lotesConErrores.size());

            for (LoteDE lote : lotesConErrores) {
                try {
                    // Verificar que no haya pasado el límite de intentos
                    if (lote.getIntentos() >= 5) {
                        log.warn("Lote ID {} alcanzó límite máximo de intentos ({}), cambiando a ERROR_PERMANENTE",
                                lote.getId(), lote.getIntentos());

                        lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                        loteDEService.save(lote);
                        continue;
                    }

                    // Verificar que haya pasado suficiente tiempo desde el último intento
                    if (lote.getFechaUltimoIntento() != null &&
                        lote.getFechaUltimoIntento().isAfter(LocalDateTime.now().minusMinutes(15))) {
                        log.debug("Lote ID {} intentado recientemente, saltando", lote.getId());
                        continue;
                    }

                    log.info("Reintentando envío de lote ID: {} (intento {})", lote.getId(), lote.getIntentos() + 1);
                    sifenService.enviarLote(lote);

                } catch (Exception e) {
                    log.error("Error al reintentar lote ID {}: {}", lote.getId(), e.getMessage());
                    // Continuar con el siguiente lote
                }
            }

        } catch (Exception e) {
            log.error("Error en job de reintento de lotes con errores: {}", e.getMessage(), e);
        }
    }

    /**
     * Job programado para limpiar lotes antiguos.
     * Se ejecuta diariamente a las 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *") // Todos los días a las 2:00 AM
    public void limpiarLotesAntiguos() {
        log.info("Ejecutando job: limpiar lotes antiguos");

        try {
            LocalDateTime limite = LocalDateTime.now().minusDays(30);

            // Buscar lotes antiguos que ya fueron procesados
            List<LoteDE> lotesAntiguos = loteDEService.findLotesAntiguosProcesados(limite);

            if (lotesAntiguos.isEmpty()) {
                log.debug("No hay lotes antiguos para limpiar");
                return;
            }

            log.info("Eliminando {} lotes antiguos", lotesAntiguos.size());

            for (LoteDE lote : lotesAntiguos) {
                try {
                    lote.setActivo(false);
                    loteDEService.save(lote);
                    log.debug("Lote ID {} marcado como inactivo", lote.getId());

                } catch (Exception e) {
                    log.error("Error al limpiar lote ID {}: {}", lote.getId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error en job de limpieza de lotes antiguos: {}", e.getMessage(), e);
        }
    }

    /**
     * Método para ejecutar manualmente el procesamiento de lotes.
     * Útil para testing o ejecución manual.
     */
    public void procesarLotesManualmente() {
        log.info("Procesando lotes manualmente");

        try {
            enviarLotesPendientes();
            consultarLotesEnProceso();
            reintentarLotesConErrores();

        } catch (Exception e) {
            log.error("Error en procesamiento manual de lotes: {}", e.getMessage(), e);
        }
    }
}
