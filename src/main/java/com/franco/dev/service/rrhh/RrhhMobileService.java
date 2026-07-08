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
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.rrhh.dto.ResumenRrhhMobileDto;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-service RRHH para mobile. Todos los endpoints scopean por usuarioId
 * (el mobile conoce su usuario logueado). Reutiliza los servicios existentes
 * SIN modificarlos; crea solicitudes en estado inicial (SOLICITADO/SOLICITADA)
 * para que el desktop/directivo las apruebe. Regla del sufijo Mobile.
 */
@Service
@AllArgsConstructor
public class RrhhMobileService {

    private final FuncionarioService funcionarioService;
    private final LiquidacionSueldoService liquidacionSueldoService;
    private final ValeService valeService;
    private final VacacionService vacacionService;
    private final MotivoValeService motivoValeService;
    private final JornadaService jornadaService;

    private Funcionario funcionarioDe(Long usuarioId) {
        Funcionario f = funcionarioService.findByUsuarioId(usuarioId);
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
        return valeService.save(v);
    }

    @Transactional
    public VacacionPeriodo solicitarVacacion(Long usuarioId, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new GraphQLException("Rango de fechas invalido");
        }
        Funcionario f = funcionarioDe(usuarioId);
        Vacacion vac = vacacionService.devengar(f.getId());
        return vacacionService.programarPeriodo(vac.getId(), desde, hasta,
                VacacionPeriodoEstado.SOLICITADA, "SOLICITUD MOBILE");
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
