package com.franco.dev.service.rrhh;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.MotivoVale;
import com.franco.dev.domain.rrhh.Vacacion;
import com.franco.dev.domain.rrhh.VacacionPeriodo;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.domain.rrhh.enums.VacacionPeriodoEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.configuracion.NotificacionPreferenciaService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.dto.ResumenRrhhMobileDto;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Self-service RRHH para mobile. Todos los endpoints scopean por usuarioId
 * (el mobile conoce su usuario logueado). Reutiliza los servicios existentes
 * SIN modificarlos; crea solicitudes en estado inicial (SOLICITADO/SOLICITADA)
 * para que el desktop/directivo las apruebe. Regla del sufijo Mobile.
 */
@Service
@AllArgsConstructor
public class RrhhMobileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RrhhMobileService.class);
    private static final String TIPO_NOTIF_SOLICITUD = "RRHH_SOLICITUD";

    private final FuncionarioService funcionarioService;
    private final UsuarioService usuarioService;
    private final LiquidacionSueldoService liquidacionSueldoService;
    private final ValeService valeService;
    private final VacacionService vacacionService;
    private final MotivoValeService motivoValeService;
    private final JornadaService jornadaService;
    private final PushNotificationService pushNotificationService;
    private final NotificacionPreferenciaService notificacionPreferenciaService;

    private Funcionario funcionarioDe(Long usuarioId) {
        // OJO: funcionario.usuario_id es "creado_por" (auditoria), NO la cuenta del
        // empleado. El vinculo empleado<->login es la persona compartida:
        // usuario.persona_id == funcionario.persona_id (ambos UNIQUE). Usar
        // findByUsuarioId aca devolveria a otro empleado, o reventaria con
        // NonUniqueResultException si ese usuario creo varios funcionarios.
        Usuario u = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
        Funcionario f = (u != null && u.getPersona() != null)
                ? funcionarioService.findByPersonaId(u.getPersona().getId())
                : null;
        if (f == null) throw new GraphQLException("No se encontro un funcionario para el usuario");
        return f;
    }

    // ---------- Consulta ----------

    @Transactional(readOnly = true)
    public List<LiquidacionSueldo> misRecibos(Long usuarioId) {
        Funcionario f = funcionarioDe(usuarioId);
        List<LiquidacionSueldo> res = new ArrayList<>();
        for (LiquidacionSueldo l : liquidacionSueldoService.findByFuncionarioId(f.getId())) {
            if (l.getEstado() == LiquidacionSueldoEstado.PAGADA) res.add(l);
        }
        return res;
    }

    @Transactional(readOnly = true)
    public List<Vale> misVales(Long usuarioId) {
        return valeService.findByFuncionarioId(funcionarioDe(usuarioId).getId());
    }

    @Transactional(readOnly = true)
    public List<Vacacion> misVacaciones(Long usuarioId) {
        return vacacionService.findByFuncionarioId(funcionarioDe(usuarioId).getId());
    }

    @Transactional(readOnly = true)
    public List<Jornada> misMarcaciones(Long usuarioId) {
        return jornadaService.findByUsuarioId(usuarioId);
    }

    @Transactional(readOnly = true)
    public ResumenRrhhMobileDto miResumen(Long usuarioId) {
        Funcionario f = funcionarioDe(usuarioId);
        ResumenRrhhMobileDto r = new ResumenRrhhMobileDto();
        r.setFuncionarioId(f.getId());
        r.setNombre(f.getPersona() != null ? f.getPersona().getNombre() : null);

        int saldo = 0;
        for (Vacacion v : vacacionService.findByFuncionarioId(f.getId())) {
            if (Boolean.TRUE.equals(v.getPrescrita())) continue;
            int gen = v.getDiasGenerados() != null ? v.getDiasGenerados() : 0;
            int goz = v.getDiasGozados() != null ? v.getDiasGozados() : 0;
            saldo += Math.max(0, gen - goz);
        }
        r.setSaldoVacacionesDias(saldo);

        long valesCant = 0;
        BigDecimal valesMonto = BigDecimal.ZERO;
        for (Vale v : valeService.findByFuncionarioId(f.getId())) {
            if (v.getEstado() == ValeEstado.SOLICITADO || v.getEstado() == ValeEstado.CONFIRMADO) {
                valesCant++;
                if (v.getMonto() != null) valesMonto = valesMonto.add(v.getMonto());
            }
        }
        r.setValesPendientesCantidad(valesCant);
        r.setValesPendientesMonto(valesMonto);

        LiquidacionSueldo ultimo = null;
        for (LiquidacionSueldo l : liquidacionSueldoService.findByFuncionarioId(f.getId())) {
            if (l.getEstado() == LiquidacionSueldoEstado.PAGADA) { ultimo = l; break; }
        }
        if (ultimo != null) {
            r.setUltimoReciboPeriodo(ultimo.getPeriodo());
            r.setUltimoReciboNeto(ultimo.getTotalNeto());
        }
        return r;
    }

    // ---------- Solicitudes ----------

    @Transactional
    public Vale solicitarVale(Long usuarioId, Long motivoId, BigDecimal monto, Boolean esAdelanto) {
        if (monto == null || monto.signum() <= 0) throw new GraphQLException("El monto debe ser mayor a cero");
        Funcionario f = funcionarioDe(usuarioId);
        Vale v = new Vale();
        v.setFuncionario(f);
        if (motivoId != null) {
            MotivoVale m = motivoValeService.findById(motivoId).orElse(null);
            v.setMotivo(m);
        }
        v.setMonto(monto);
        v.setFecha(LocalDate.now());
        v.setEsAdelanto(esAdelanto != null ? esAdelanto : false);
        v.setEstado(ValeEstado.SOLICITADO);
        Vale guardado = valeService.save(v);
        String nombre = f.getPersona() != null ? f.getPersona().getNombre() : ("Funcionario #" + f.getId());
        notificarAprobadores("Nueva solicitud de vale",
                nombre + " solicito un vale de " + monto.toPlainString());
        return guardado;
    }

    @Transactional
    public VacacionPeriodo solicitarVacacion(Long usuarioId, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new GraphQLException("Rango de fechas invalido");
        }
        Funcionario f = funcionarioDe(usuarioId);
        Vacacion vac = vacacionService.devengar(f.getId());
        VacacionPeriodo periodo = vacacionService.programarPeriodo(vac.getId(), desde, hasta,
                VacacionPeriodoEstado.SOLICITADA, "SOLICITUD MOBILE");
        String nombre = f.getPersona() != null ? f.getPersona().getNombre() : ("Funcionario #" + f.getId());
        notificarAprobadores("Nueva solicitud de vacaciones",
                nombre + " solicito vacaciones del " + desde + " al " + hasta);
        return periodo;
    }

    /**
     * Notifica (push) a los aprobadores configurados para el tipo RRHH_SOLICITUD.
     * Resiliente: un fallo del push nunca rompe la solicitud.
     */
    private void notificarAprobadores(String titulo, String mensaje) {
        try {
            List<Usuario> aprobadores = notificacionPreferenciaService
                    .obtenerUsuariosPorTipoNotificacion(TIPO_NOTIF_SOLICITUD);
            List<Long> ids = aprobadores.stream()
                    .filter(u -> u != null && u.getId() != null)
                    .map(Usuario::getId)
                    .distinct()
                    .collect(Collectors.toList());
            if (ids.isEmpty()) return;
            pushNotificationService.enviarNotificacionPersonalizada(titulo, mensaje, "ESPECIFICOS", ids);
        } catch (Exception e) {
            LOGGER.warn("No se pudo enviar el push a los aprobadores RRHH: {}", e.getMessage());
        }
    }

    // ---------- Aprobaciones (directivo) ----------

    @Transactional(readOnly = true)
    public List<Vale> valesPendientesAprobacion() {
        return valeService.findByEstado(ValeEstado.SOLICITADO);
    }

    @Transactional(readOnly = true)
    public List<VacacionPeriodo> vacacionesPendientesAprobacion() {
        return vacacionService.findPeriodosPorEstado(VacacionPeriodoEstado.SOLICITADA);
    }

    /** Aprueba un periodo de vacaciones (sin movimiento de dinero). */
    @Transactional
    public VacacionPeriodo aprobarVacacion(Long periodoId, Long aprobadorUsuarioId) {
        return vacacionService.aprobarPeriodo(periodoId, aprobadorUsuarioId);
    }
}
