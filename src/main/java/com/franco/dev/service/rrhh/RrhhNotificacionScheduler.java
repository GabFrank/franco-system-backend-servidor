package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.FuncionarioDocumento;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.Vacacion;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.repository.rrhh.FuncionarioDocumentoRepository;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import com.franco.dev.repository.rrhh.PrestamoCuotaRepository;
import com.franco.dev.repository.rrhh.VacacionRepository;
import com.franco.dev.service.configuracion.NotificacionPreferenciaService;
import com.franco.dev.service.personas.FuncionarioService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final FuncionarioService funcionarioService;
    private final FuncionarioDocumentoRepository funcionarioDocumentoRepository;
    private final VacacionRepository vacacionRepository;
    private final ConfiguracionRrhhService configuracionRrhhService;
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

            // cumpleaños de hoy
            LocalDate hoy = LocalDate.now();
            long cumpleHoy = 0;
            for (Funcionario f : funcionarioService.findAll2()) {
                if (Boolean.TRUE.equals(f.getActivo()) && f.getPersona() != null
                        && f.getPersona().getNacimiento() != null) {
                    java.time.LocalDateTime n = f.getPersona().getNacimiento();
                    if (n.getMonthValue() == hoy.getMonthValue() && n.getDayOfMonth() == hoy.getDayOfMonth()) {
                        cumpleHoy++;
                    }
                }
            }
            if (cumpleHoy > 0) lineas.add(cumpleHoy + " cumpleaños hoy");

            // documentos por vencer (proximos N dias)
            int docAvisoDias = configuracionRrhhService.getNumber("DOCUMENTO_AVISO_VENCIMIENTO_DIAS", new BigDecimal("30")).intValue();
            long docsPorVencer = funcionarioDocumentoRepository
                    .findByAnuladoFalseAndVencimientoBetween(hoy, hoy.plusDays(docAvisoDias)).size();
            if (docsPorVencer > 0) lineas.add(docsPorVencer + " documento(s) por vencer");

            // vacaciones por prescribir (proximas)
            int prescripcionMeses = configuracionRrhhService.getNumber("PRESCRIPCION_VACACIONES_MESES", new BigDecimal("24")).intValue();
            int vacAvisoDias = configuracionRrhhService.getNumber("VACACION_AVISO_PRESCRIPCION_DIAS", new BigDecimal("60")).intValue();
            LocalDate limiteVac = hoy.plusDays(vacAvisoDias);
            long vacPorVencer = 0;
            for (Vacacion v : vacacionRepository.findByPrescritaFalse()) {
                int gen = v.getDiasGenerados() != null ? v.getDiasGenerados() : 0;
                int goz = v.getDiasGozados() != null ? v.getDiasGozados() : 0;
                if (gen - goz <= 0 || v.getFechaCorte() == null) continue;
                if (!v.getFechaCorte().plusMonths(prescripcionMeses).isAfter(limiteVac)) vacPorVencer++;
            }
            if (vacPorVencer > 0) lineas.add(vacPorVencer + " vacacion(es) por prescribir");

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
