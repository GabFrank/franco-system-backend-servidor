package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.JornadaNovedad;
import com.franco.dev.domain.rrhh.Vacacion;
import com.franco.dev.domain.rrhh.VacacionPeriodo;
import com.franco.dev.domain.rrhh.VacacionVenta;
import com.franco.dev.domain.rrhh.enums.JornadaNovedadTipo;
import com.franco.dev.domain.rrhh.enums.VacacionPeriodoEstado;
import com.franco.dev.domain.rrhh.enums.VacacionVentaEstado;
import com.franco.dev.repository.rrhh.VacacionPeriodoRepository;
import com.franco.dev.repository.rrhh.VacacionRepository;
import com.franco.dev.repository.rrhh.VacacionVentaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.personas.FuncionarioService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class VacacionService extends CrudService<Vacacion, VacacionRepository, Long> {

    private final VacacionRepository repository;
    private final VacacionPeriodoRepository periodoRepository;
    private final VacacionVentaRepository ventaRepository;
    private final FuncionarioService funcionarioService;
    private final ConfiguracionRrhhService configuracionRrhhService;
    private final JornadaNovedadService jornadaNovedadService;

    @Override
    public VacacionRepository getRepository() {
        return repository;
    }

    public List<Vacacion> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByAnioServicioDesc(funcionarioId);
    }

    public List<VacacionPeriodo> findPeriodos(Long vacacionId) {
        return periodoRepository.findByVacacionIdOrderByFechaDesdeAsc(vacacionId);
    }

    public List<VacacionPeriodo> findPeriodosPorEstado(VacacionPeriodoEstado estado) {
        return periodoRepository.findByEstadoOrderByFechaDesdeAsc(estado);
    }

    public List<VacacionVenta> findVentas(Long vacacionId) {
        return ventaRepository.findByVacacionIdOrderByFechaDesc(vacacionId);
    }

    /** Dias de vacaciones segun antiguedad (ley PY, parametrizable). */
    private int diasPorAntiguedad(int anios) {
        int dias5 = configuracionRrhhService.getNumber("DIAS_VACACIONES_HASTA_5A", new BigDecimal("12")).intValue();
        int dias10 = configuracionRrhhService.getNumber("DIAS_VACACIONES_5_10A", new BigDecimal("18")).intValue();
        int diasMas = configuracionRrhhService.getNumber("DIAS_VACACIONES_MAS_10A", new BigDecimal("30")).intValue();
        return com.franco.dev.service.rrhh.builder.VacacionDevengamientoCalculator
                .diasPorAntiguedad(anios, dias5, dias10, diasMas);
    }

    /**
     * Devenga (genera) la vacacion del anio de servicio corriente del funcionario
     * segun su antiguedad. Idempotente: si ya existe la del anio, la devuelve.
     */
    @Transactional
    public Vacacion devengar(Long funcionarioId) {
        Funcionario f = funcionarioService.findById(funcionarioId).orElse(null);
        if (f == null || f.getFechaIngreso() == null) {
            throw new GraphQLException("Funcionario o fecha de ingreso no disponible");
        }
        LocalDate ingreso = f.getFechaIngreso().toLocalDate();
        int anioServicio = (int) Math.max(1, ChronoUnit.YEARS.between(ingreso, LocalDate.now()) + 1);

        Optional<Vacacion> existente = repository.findByFuncionarioIdAndAnioServicio(funcionarioId, anioServicio);
        if (existente.isPresent()) return existente.get();

        int dias = diasPorAntiguedad(anioServicio - 1);
        Vacacion v = new Vacacion();
        v.setFuncionario(f);
        v.setAnioServicio(anioServicio);
        v.setDiasGenerados(dias);
        v.setDiasGozados(0);
        v.setFechaCorte(ingreso.plusYears(anioServicio));
        v.setPrescrita(false);
        v.setCreadoEn(LocalDateTime.now());
        return repository.save(v);
    }

    /** Programa un periodo de vacaciones (SOLICITADA por defecto para self-service). */
    @Transactional
    public VacacionPeriodo programarPeriodo(Long vacacionId, LocalDate desde, LocalDate hasta,
                                            VacacionPeriodoEstado estado, String observacion) {
        Vacacion v = repository.findById(vacacionId)
                .orElseThrow(() -> new GraphQLException("Vacacion no encontrada"));
        int dias = (int) (ChronoUnit.DAYS.between(desde, hasta) + 1);
        int gozados = v.getDiasGozados() != null ? v.getDiasGozados() : 0;
        if (gozados + dias > (v.getDiasGenerados() != null ? v.getDiasGenerados() : 0)) {
            throw new GraphQLException("Los dias del periodo superan los dias disponibles");
        }
        VacacionPeriodo p = new VacacionPeriodo();
        p.setVacacion(v);
        p.setFechaDesde(desde);
        p.setFechaHasta(hasta);
        p.setDiasUsados(dias);
        p.setEstado(estado != null ? estado : VacacionPeriodoEstado.SOLICITADA);
        p.setNovedadesGeneradas(false);
        p.setObservacion(observacion != null ? observacion.toUpperCase() : null);
        p.setCreadoEn(LocalDateTime.now());
        return periodoRepository.save(p);
    }

    @Transactional
    public VacacionPeriodo aprobarPeriodo(Long periodoId, Long autorizadoPorId) {
        VacacionPeriodo p = periodoRepository.findById(periodoId)
                .orElseThrow(() -> new GraphQLException("Periodo no encontrado"));
        p.setEstado(VacacionPeriodoEstado.PROGRAMADA);
        if (autorizadoPorId != null) {
            funcionarioService.findById(autorizadoPorId); // no-op guard
        }
        return periodoRepository.save(p);
    }

    /**
     * Marca un periodo como GOZADA: genera una novedad de jornada VACACION por
     * cada dia del rango (idempotente por el flag novedades_generadas) y suma
     * los dias gozados a la vacacion.
     */
    @Transactional
    public VacacionPeriodo marcarGozada(Long periodoId) {
        VacacionPeriodo p = periodoRepository.findById(periodoId)
                .orElseThrow(() -> new GraphQLException("Periodo no encontrado"));
        Vacacion v = p.getVacacion();

        if (!Boolean.TRUE.equals(p.getNovedadesGeneradas())) {
            Long funcionarioId = v.getFuncionario() != null ? v.getFuncionario().getId() : null;
            LocalDate d = p.getFechaDesde();
            while (funcionarioId != null && d != null && !d.isAfter(p.getFechaHasta())) {
                JornadaNovedad n = new JornadaNovedad();
                n.setFuncionario(v.getFuncionario());
                n.setFecha(d);
                n.setTipo(JornadaNovedadTipo.VACACION);
                n.setObservacion("VACACION (PERIODO #" + p.getId() + ")");
                jornadaNovedadService.save(n);
                d = d.plusDays(1);
            }
            p.setNovedadesGeneradas(true);
            int gozados = v.getDiasGozados() != null ? v.getDiasGozados() : 0;
            v.setDiasGozados(gozados + (p.getDiasUsados() != null ? p.getDiasUsados() : 0));
            repository.save(v);
        }
        p.setEstado(VacacionPeriodoEstado.GOZADA);
        return periodoRepository.save(p);
    }

    /**
     * Vende dias de vacaciones no gozados: crea una VacacionVenta PENDIENTE
     * valorizada a salarioDiario x dias. Se cobra como HABER en la liquidacion.
     */
    @Transactional
    public VacacionVenta venderDias(Long vacacionId, Integer dias, String observacion) {
        Vacacion v = repository.findById(vacacionId)
                .orElseThrow(() -> new GraphQLException("Vacacion no encontrada"));
        int disponibles = (v.getDiasGenerados() != null ? v.getDiasGenerados() : 0)
                - (v.getDiasGozados() != null ? v.getDiasGozados() : 0);
        if (dias == null || dias <= 0 || dias > disponibles) {
            throw new GraphQLException("Cantidad de dias a vender invalida");
        }
        BigDecimal salarioDiario = BigDecimal.ZERO;
        if (v.getFuncionario() != null && v.getFuncionario().getSueldo() != null) {
            salarioDiario = new BigDecimal(v.getFuncionario().getSueldo().toString())
                    .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
        }
        VacacionVenta venta = new VacacionVenta();
        venta.setVacacion(v);
        venta.setDias(dias);
        venta.setMonto(salarioDiario.multiply(new BigDecimal(dias)));
        venta.setFecha(LocalDate.now());
        venta.setEstado(VacacionVentaEstado.PENDIENTE);
        venta.setObservacion(observacion != null ? observacion.toUpperCase() : null);
        venta.setCreadoEn(LocalDateTime.now());
        return ventaRepository.save(venta);
    }

    /** Marca prescritas las vacaciones cuyo corte supero los meses configurados. */
    @Transactional
    public int prescribir() {
        int meses = configuracionRrhhService.getNumber("PRESCRIPCION_VACACIONES_MESES", new BigDecimal("24")).intValue();
        LocalDate limite = LocalDate.now().minusMonths(meses);
        int n = 0;
        for (Vacacion v : repository.findAll2()) {
            if (!Boolean.TRUE.equals(v.getPrescrita()) && v.getFechaCorte() != null && v.getFechaCorte().isBefore(limite)) {
                int disponibles = (v.getDiasGenerados() != null ? v.getDiasGenerados() : 0)
                        - (v.getDiasGozados() != null ? v.getDiasGozados() : 0);
                if (disponibles > 0) {
                    v.setPrescrita(true);
                    repository.save(v);
                    n++;
                }
            }
        }
        return n;
    }

    @Override
    public Vacacion save(Vacacion entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getDiasGozados() == null) entity.setDiasGozados(0);
        if (entity.getPrescrita() == null) entity.setPrescrita(false);
        return super.save(entity);
    }
}
