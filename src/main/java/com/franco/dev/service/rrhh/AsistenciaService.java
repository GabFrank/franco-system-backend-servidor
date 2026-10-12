package com.franco.dev.service.rrhh;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.enums.Dia;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.Feriado;
import com.franco.dev.domain.rrhh.FuncionarioEgresoHistorico;
import com.franco.dev.domain.rrhh.Justificativo;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.repository.rrhh.FuncionarioEgresoHistoricoRepository;
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.builder.AsistenciaCalculator;
import com.franco.dev.service.rrhh.builder.AsistenciaCalculator.DiaLaborable;
import com.franco.dev.service.rrhh.dto.AsistenciaResumenDto;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puntuacion de asistencia del legajo. Solo informativa: no persiste nada, no descuenta ni
 * penaliza. Junta los datos del mes y delega la aritmetica en {@link AsistenciaCalculator}.
 */
@Service
@AllArgsConstructor
public class AsistenciaService {

    private final FuncionarioService funcionarioService;
    private final UsuarioService usuarioService;
    private final HorarioRepository horarioRepository;
    private final FuncionarioEgresoHistoricoRepository egresoRepository;
    private final FeriadoService feriadoService;
    private final JornadaService jornadaService;
    private final JustificativoService justificativoService;
    private final ConfiguracionRrhhService configuracionRrhhService;

    @Transactional(readOnly = true)
    public AsistenciaResumenDto calcular(Long funcionarioId, int anho, int mes) {
        return calcular(funcionarioId, anho, mes, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public AsistenciaResumenDto calcular(Long funcionarioId, int anho, int mes, LocalDate hoy) {
        if (mes < 1 || mes > 12) throw new GraphQLException("Mes invalido: " + mes);
        Funcionario funcionario = funcionarioService.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado"));

        // Las jornadas son de la cuenta que marca. Se llega a ella por la persona compartida,
        // NO por funcionario.usuario_id, que es "creado_por" (auditoria).
        Usuario usuario = funcionario.getPersona() != null
                ? usuarioService.findByPersonaId(funcionario.getPersona().getId())
                : null;

        List<Horario> horarios = horarios(funcionario, usuario);
        boolean tieneHorario = usuario != null && !horarios.isEmpty();

        LocalDate ingreso = funcionario.getFechaIngreso() != null ? funcionario.getFechaIngreso().toLocalDate() : null;
        LocalDate egreso = egresoRepository.findFirstByFuncionarioIdAndRevertidoEnIsNullOrderByIdDesc(funcionarioId)
                .map(FuncionarioEgresoHistorico::getFechaEgreso)
                .map(f -> f.toLocalDate())
                .orElse(null);
        LocalDate[] rango = AsistenciaCalculator.rango(anho, mes, ingreso, egreso, hoy);

        List<DiaLaborable> dias = tieneHorario && rango != null
                ? diasEsperados(funcionarioId, usuario, horarios, ingreso, rango[0], rango[1], hoy)
                : Collections.emptyList();

        long tolerancia = configuracionRrhhService.getNumber("TOLERANCIA_TARDANZA_MIN", BigDecimal.ZERO).longValue();
        BigDecimal pesoFalta = configuracionRrhhService.getNumber("ASISTENCIA_PESO_FALTA",
                AsistenciaCalculator.PESO_FALTA_DEFAULT);
        BigDecimal pesoTardanza = configuracionRrhhService.getNumber("ASISTENCIA_PESO_TARDANZA",
                AsistenciaCalculator.PESO_TARDANZA_DEFAULT);

        return AsistenciaCalculator.calcular(anho, mes, tieneHorario, dias, tolerancia, pesoFalta, pesoTardanza);
    }

    /** El horario del funcionario mas los que tenga asignados su usuario (alternativos por dia). */
    private List<Horario> horarios(Funcionario funcionario, Usuario usuario) {
        List<Horario> horarios = new ArrayList<>();
        if (funcionario.getHorario() != null) horarios.add(funcionario.getHorario());
        if (usuario != null) {
            for (Horario h : horarioRepository.findByUsuarioIdOrderByIdDesc(usuario.getId())) {
                if (h.getHoraEntrada() != null) horarios.add(h);
            }
        }
        return horarios;
    }

    /**
     * Dias del rango en que se esperaba al funcionario. Si su horario rige los siete dias de la
     * semana se le descuenta el franco semanal; con un horario de seis dias o menos el dia libre
     * ya esta fuera del horario y no se descuenta otro.
     */
    private List<DiaLaborable> diasEsperados(Long funcionarioId, Usuario usuario, List<Horario> horarios,
                                             LocalDate ingreso, LocalDate desde, LocalDate hasta, LocalDate hoy) {
        boolean sieteDias = true;
        for (DayOfWeek d : DayOfWeek.values()) sieteDias = sieteDias && rigeAlgunHorario(horarios, d);
        if (!sieteDias) return diasLaborables(funcionarioId, usuario, horarios, desde, hasta, hoy);

        // el franco se decide por semana completa: se mira desde el lunes aunque caiga en el mes anterior
        LocalDate lunes = desde.with(DayOfWeek.MONDAY);
        if (ingreso != null && ingreso.isAfter(lunes)) lunes = ingreso;
        List<DiaLaborable> dias = new ArrayList<>();
        for (DiaLaborable d : AsistenciaCalculator.sinFrancoSemanal(
                diasLaborables(funcionarioId, usuario, horarios, lunes, hasta, hoy))) {
            if (!d.getFecha().isBefore(desde)) dias.add(d);
        }
        return dias;
    }

    private List<DiaLaborable> diasLaborables(Long funcionarioId, Usuario usuario, List<Horario> horarios,
                                              LocalDate desde, LocalDate hasta, LocalDate hoy) {
        Set<LocalDate> feriados = new HashSet<>();
        for (Feriado f : feriadoService.findByFechaBetween(desde, hasta)) {
            if (!Boolean.FALSE.equals(f.getActivo())) feriados.add(f.getFecha());
        }

        // Con mas de una jornada el mismo dia cuenta la primera: la tardanza de las siguientes
        // se mide contra la misma hora de entrada y no dice nada de la puntualidad.
        Map<LocalDate, Jornada> jornadas = new HashMap<>();
        for (Jornada j : jornadaService.findByUsuarioIdAndFechaRange(usuario.getId(), desde.toString(), hasta.toString())) {
            if (j.getMarcacionEntrada() == null || j.getFecha() == null) continue;
            Jornada actual = jornadas.get(j.getFecha());
            if (actual == null || j.getId() < actual.getId()) jornadas.put(j.getFecha(), j);
        }

        List<Justificativo> justificativos = justificativoService.findByFuncionarioIdAndFechaBetween(funcionarioId, desde, hasta);

        List<DiaLaborable> dias = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            if (feriados.contains(fecha) || !rigeAlgunHorario(horarios, fecha.getDayOfWeek())) continue;

            Jornada jornada = jornadas.get(fecha);
            // hoy todavia no termino: sin marcar no es falta
            if (jornada == null && fecha.equals(hoy)) continue;

            boolean delSistema = false, justificado = false;
            String nombre = null;
            for (Justificativo x : justificativos) {
                if (x.getTipo() == null || !aplica(x, fecha, jornada)) continue;
                if (Boolean.TRUE.equals(x.getTipo().getGeneradoPorSistema())) delSistema = true;
                boolean evita = Boolean.TRUE.equals(x.getTipo().getEvitaPenalizacion());
                if (nombre == null || (evita && !justificado)) nombre = x.getTipo().getNombre();
                justificado = justificado || evita;
            }
            // vacaciones y feriados cargados como justificativo: ese dia no se esperaba al funcionario
            if (delSistema) continue;

            dias.add(new DiaLaborable(fecha, jornada != null,
                    jornada != null ? minutos(jornada.getMinutosLlegadaTardia()) : 0,
                    jornada != null ? minutos(jornada.getMinutosLlegadaTardiaAlmuerzo()) : 0,
                    justificado, nombre));
        }
        return dias;
    }

    /** Un justificativo vale para el dia por su fecha o por estar atado a la jornada de ese dia. */
    private boolean aplica(Justificativo x, LocalDate fecha, Jornada jornada) {
        if (fecha.equals(x.getFecha())) return true;
        return jornada != null && jornada.getId().equals(x.getJornadaId())
                && jornada.getSucursalId() != null && jornada.getSucursalId().equals(x.getSucursalId());
    }

    /** Mismo criterio que HorarioResolver: un horario sin dias cargados rige todos los dias. */
    private boolean rigeAlgunHorario(List<Horario> horarios, DayOfWeek dayOfWeek) {
        Dia dia = Dia.values()[dayOfWeek.getValue()]; // Dia: TODOS, LUNES..DOMINGO; DayOfWeek: 1=MONDAY
        for (Horario h : horarios) {
            if (h.getDias() == null || h.getDias().isEmpty()) return true;
            if (h.getDias().contains(dia) || h.getDias().contains(Dia.TODOS)) return true;
        }
        return false;
    }

    private long minutos(Long valor) {
        return valor != null ? Math.max(0, valor) : 0;
    }
}
