package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.service.financiero.EventoCancelacionDEService;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class EventoSifenScheduler {

    @Value("${sifen.enabled:false}")
    private Boolean sifenEnabled;

    @Autowired
    private EventoCancelacionDEService eventoCancelacionDEService;

    @Autowired
    private SifenEventoService sifenEventoService;

    /**
     * Se ejecuta cada 5 minutos para consultar el estado de los eventos de cancelación pendientes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutos
    public void consultarEventosDeCancelacionPendientes() {
        // Verificar si SIFEN está habilitado
        if (!sifenEnabled) {
            log.debug("Scheduler de eventos SIFEN deshabilitado");
            return;
        }

        log.info("SCHEDULER: Iniciando consulta de eventos de cancelación pendientes...");
        List<EventoCancelacionDE> eventosPendientes = eventoCancelacionDEService.findEventosPendientes();

        if (eventosPendientes.isEmpty()) {
            log.info("SCHEDULER: No se encontraron eventos pendientes.");
            return;
        }

        log.info("SCHEDULER: Se encontraron {} eventos pendientes.", eventosPendientes.size());

        for (EventoCancelacionDE evento : eventosPendientes) {
            try {
                sifenEventoService.consultarYActualizarEventoCancelacion(evento);
            } catch (SifenException e) {
                log.error("SCHEDULER: Error de Sifen al procesar el evento ID {}: {}", evento.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("SCHEDULER: Error inesperado al procesar el evento ID {}: {}", evento.getId(), e.getMessage(), e);
            }
        }
        log.info("SCHEDULER: Fin de la consulta de eventos.");
    }
}
