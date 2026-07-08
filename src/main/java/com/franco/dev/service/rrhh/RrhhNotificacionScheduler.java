package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import com.franco.dev.repository.rrhh.PrestamoCuotaRepository;
import com.franco.dev.service.configuracion.NotificacionPreferenciaService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Job diario que genera alertas del modulo RRHH y las envia (push) a los
 * usuarios configurados para el tipo RRHH_ALERTA. Envia UN resumen por dia
 * si hay algo para avisar (no spamea). Resiliente: un fallo no corta el job.
 */
@Service
@AllArgsConstructor
public class RrhhNotificacionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RrhhNotificacionScheduler.class);
    private static final String TIPO_ALERTA = "RRHH_ALERTA";

    private final PrestamoCuotaRepository prestamoCuotaRepository;
    private final LiquidacionSueldoRepository liquidacionSueldoRepository;
    private final PushNotificationService pushNotificationService;
    private final NotificacionPreferenciaService notificacionPreferenciaService;

    // Todos los dias a las 07:00 (hora del servidor).
    @Scheduled(cron = "${rrhh.notificacion.cron:0 0 7 * * ?}")
    public void generarAlertasDiarias() {
        try {
            List<String> lineas = new ArrayList<>();

            long cuotasVencidas = prestamoCuotaRepository.findByEstado(PrestamoCuotaEstado.VENCIDA).size();
            if (cuotasVencidas > 0) {
                lineas.add(cuotasVencidas + " cuota(s) de prestamo vencida(s)");
            }

            long liquidacionesPendientes = 0;
            for (LiquidacionSueldo l : liquidacionSueldoRepository.findByEstadoOrderByPeriodoDesc(LiquidacionSueldoEstado.BORRADOR)) {
                if (l != null) liquidacionesPendientes++;
            }
            for (LiquidacionSueldo l : liquidacionSueldoRepository.findByEstadoOrderByPeriodoDesc(LiquidacionSueldoEstado.APROBADA)) {
                if (l != null) liquidacionesPendientes++;
            }
            if (liquidacionesPendientes > 0) {
                lineas.add(liquidacionesPendientes + " liquidacion(es) pendiente(s) de pago");
            }

            if (lineas.isEmpty()) {
                LOGGER.info("RrhhNotificacionScheduler: sin alertas para hoy");
                return;
            }

            String mensaje = String.join(" · ", lineas);
            enviarAlerta("Alertas RRHH", mensaje);
            LOGGER.info("RrhhNotificacionScheduler: alerta enviada -> {}", mensaje);
        } catch (Exception e) {
            LOGGER.error("RrhhNotificacionScheduler: error generando alertas diarias", e);
        }
    }

    private void enviarAlerta(String titulo, String mensaje) {
        try {
            List<Usuario> destinatarios = notificacionPreferenciaService.obtenerUsuariosPorTipoNotificacion(TIPO_ALERTA);
            List<Long> ids = destinatarios.stream()
                    .filter(u -> u != null && u.getId() != null)
                    .map(Usuario::getId)
                    .distinct()
                    .collect(Collectors.toList());
            if (ids.isEmpty()) return;
            pushNotificationService.enviarNotificacionPersonalizada(titulo, mensaje, "ESPECIFICOS", ids);
        } catch (Exception e) {
            LOGGER.warn("RrhhNotificacionScheduler: no se pudo enviar la alerta: {}", e.getMessage());
        }
    }
}
