package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Aguinaldo;
import com.franco.dev.domain.rrhh.HoraExtra;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.Penalizacion;
import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
import com.franco.dev.domain.rrhh.enums.PrestamoEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.rrhh.AguinaldoRepository;
import com.franco.dev.repository.rrhh.HoraExtraRepository;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import com.franco.dev.repository.rrhh.PenalizacionRepository;
import com.franco.dev.repository.rrhh.PrestamoCuotaRepository;
import com.franco.dev.repository.rrhh.PrestamoRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.rrhh.dto.DashboardRrhhKpisDto;
import com.franco.dev.service.rrhh.dto.RrhhCumpleanosDto;
import com.franco.dev.service.rrhh.dto.RrhhFuncionarioIncompletoDto;
import com.franco.dev.service.rrhh.dto.RrhhIncompletosPageDto;
import com.franco.dev.service.rrhh.dto.RrhhRankingItemDto;
import com.franco.dev.service.rrhh.dto.RrhhSeriePuntoDto;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calcula los KPIs del dashboard de RRHH para un período (YYYY-MM),
 * agregando sobre los repositorios existentes. Solo lectura.
 */
@Service
@AllArgsConstructor
public class DashboardRrhhService {

    private final FuncionarioService funcionarioService;
    private final LiquidacionSueldoRepository liquidacionSueldoRepository;
    private final ValeRepository valeRepository;
    private final PrestamoRepository prestamoRepository;
    private final PrestamoCuotaRepository prestamoCuotaRepository;
    private final PenalizacionRepository penalizacionRepository;
    private final HoraExtraRepository horaExtraRepository;
    private final AguinaldoRepository aguinaldoRepository;
    private final com.franco.dev.repository.rrhh.VacacionRepository vacacionRepository;
    private final ConfiguracionRrhhService configuracionRrhhService;

    @Transactional(readOnly = true)
    public DashboardRrhhKpisDto getKpis(String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}-\\d{2}")) {
            throw new GraphQLException("Periodo invalido, se espera 'YYYY-MM'");
        }
        int anio = Integer.parseInt(periodo.substring(0, 4));
        YearMonth ym = YearMonth.parse(periodo);
        LocalDate desde = ym.atDay(1);
        LocalDate hasta = ym.atEndOfMonth();

        DashboardRrhhKpisDto k = new DashboardRrhhKpisDto();
        k.setPeriodo(periodo);

        // funcionarios activos + cumpleaños del mes del período
        long activos = 0;
        long cumpleanos = 0;
        int mesPeriodo = ym.getMonthValue();
        for (Funcionario f : funcionarioService.findAll2()) {
            if (Boolean.TRUE.equals(f.getActivo())) {
                activos++;
                if (f.getPersona() != null && f.getPersona().getNacimiento() != null
                        && f.getPersona().getNacimiento().getMonthValue() == mesPeriodo) {
                    cumpleanos++;
                }
            }
        }
        k.setFuncionariosActivos(activos);
        k.setCumpleanosDelMes(cumpleanos);

        // vacaciones por vencer (no prescritas, con saldo, cercanas a prescribir)
        int prescripcionMeses = configuracionRrhhService.getNumber("PRESCRIPCION_VACACIONES_MESES", new BigDecimal("24")).intValue();
        int avisoDias = configuracionRrhhService.getNumber("VACACION_AVISO_PRESCRIPCION_DIAS", new BigDecimal("60")).intValue();
        LocalDate limite = LocalDate.now().plusDays(avisoDias);
        long vacacionesPorVencer = 0;
        for (com.franco.dev.domain.rrhh.Vacacion v : vacacionRepository.findByPrescritaFalse()) {
            int gen = v.getDiasGenerados() != null ? v.getDiasGenerados() : 0;
            int goz = v.getDiasGozados() != null ? v.getDiasGozados() : 0;
            if (gen - goz <= 0 || v.getFechaCorte() == null) continue;
            LocalDate fechaPrescripcion = v.getFechaCorte().plusMonths(prescripcionMeses);
            if (!fechaPrescripcion.isAfter(limite)) vacacionesPorVencer++;
        }
        k.setVacacionesPorVencer(vacacionesPorVencer);

        // nómina del mes + liquidaciones pendientes
        BigDecimal nomina = BigDecimal.ZERO;
        long pendientes = 0;
        for (LiquidacionSueldo l : liquidacionSueldoRepository.findByPeriodoOrderByIdAsc(periodo)) {
            if (l.getEstado() == LiquidacionSueldoEstado.APROBADA || l.getEstado() == LiquidacionSueldoEstado.PAGADA) {
                if (l.getTotalNeto() != null) nomina = nomina.add(l.getTotalNeto());
            }
            if (l.getEstado() == LiquidacionSueldoEstado.BORRADOR || l.getEstado() == LiquidacionSueldoEstado.APROBADA) {
                pendientes++;
            }
        }
        k.setNominaDelMes(nomina);
        k.setLiquidacionesPendientes(pendientes);

        // vales pendientes (solicitados + confirmados sin descontar)
        long valesCant = 0;
        BigDecimal valesMonto = BigDecimal.ZERO;
        for (ValeEstado est : new ValeEstado[]{ValeEstado.SOLICITADO, ValeEstado.CONFIRMADO}) {
            for (Vale v : valeRepository.findByEstadoOrderByFechaDesc(est)) {
                valesCant++;
                if (v.getMonto() != null) valesMonto = valesMonto.add(v.getMonto());
            }
        }
        k.setValesPendientesCantidad(valesCant);
        k.setValesPendientesMonto(valesMonto);

        // préstamos activos (saldo = total - pagado)
        long prestCant = 0;
        BigDecimal prestSaldo = BigDecimal.ZERO;
        for (Prestamo p : prestamoRepository.findByEstadoOrderByFechaInicioDesc(PrestamoEstado.ACTIVO)) {
            prestCant++;
            BigDecimal total = p.getMontoTotal() != null ? p.getMontoTotal() : BigDecimal.ZERO;
            BigDecimal pagado = p.getMontoPagado() != null ? p.getMontoPagado() : BigDecimal.ZERO;
            prestSaldo = prestSaldo.add(total.subtract(pagado).max(BigDecimal.ZERO));
        }
        k.setPrestamosActivosCantidad(prestCant);
        k.setPrestamosActivosSaldo(prestSaldo);

        // penalizaciones del mes
        long penCant = 0;
        BigDecimal penMonto = BigDecimal.ZERO;
        for (Penalizacion p : penalizacionRepository.findByFechaBetweenAndAnuladaFalse(desde, hasta)) {
            // Las amonestaciones no son penalizaciones de plata. Aca el filtro es
            // obligatorio y no defensivo: este contador suma FILAS, asi que un monto en
            // cero no alcanza para dejarlas afuera del KPI.
            if (p.getTipo() == com.franco.dev.domain.rrhh.enums.PenalizacionTipo.ADVERTENCIA) continue;
            penCant++;
            if (p.getMonto() != null) penMonto = penMonto.add(p.getMonto());
        }
        k.setPenalizacionesMesCantidad(penCant);
        k.setPenalizacionesMesMonto(penMonto);

        // horas extra del mes
        long heCant = 0;
        BigDecimal heMonto = BigDecimal.ZERO;
        for (HoraExtra x : horaExtraRepository.findByFechaBetweenAndAnuladaFalse(desde, hasta)) {
            heCant++;
            if (x.getMontoCalculado() != null) heMonto = heMonto.add(x.getMontoCalculado());
        }
        k.setHorasExtraMesCantidad(heCant);
        k.setHorasExtraMesMonto(heMonto);

        // cuotas vencidas
        k.setCuotasVencidasCantidad((long) prestamoCuotaRepository.findByEstado(PrestamoCuotaEstado.VENCIDA).size());

        // aguinaldo estimado del año
        BigDecimal aguinaldo = BigDecimal.ZERO;
        for (Aguinaldo a : aguinaldoRepository.findByAnioOrderByIdAsc(anio)) {
            if (a.getMontoCalculado() != null) aguinaldo = aguinaldo.add(a.getMontoCalculado());
        }
        k.setAguinaldoEstimadoAnio(aguinaldo);

        return k;
    }

    /**
     * Serie mensual de nómina (neto liquidado) entre dos períodos 'YYYY-MM'
     * inclusive, para el gráfico de tendencia. Solo APROBADA/PAGADA.
     */
    @Transactional(readOnly = true)
    public List<RrhhSeriePuntoDto> getNominaSeriePorMes(String periodoInicio, String periodoFin) {
        validarPeriodo(periodoInicio);
        validarPeriodo(periodoFin);
        List<RrhhSeriePuntoDto> serie = new ArrayList<>();
        for (Object[] fila : liquidacionSueldoRepository.nominaSeriePorMesRaw(periodoInicio, periodoFin)) {
            serie.add(new RrhhSeriePuntoDto(
                    fila[0] != null ? fila[0].toString() : null,
                    fila[1] != null ? ((Number) fila[1]).longValue() : 0L,
                    fila[2] != null ? new BigDecimal(fila[2].toString()) : BigDecimal.ZERO));
        }
        return serie;
    }

    /**
     * Top funcionarios por exposición financiera = vales pendientes (SOLICITADO/
     * CONFIRMADO) + saldo de préstamos activos. secundario = nº de obligaciones.
     */
    @Transactional(readOnly = true)
    public List<RrhhRankingItemDto> getTopExposicion(int limite) {
        Map<Long, RrhhRankingItemDto> acc = new LinkedHashMap<>();
        for (ValeEstado est : new ValeEstado[]{ValeEstado.SOLICITADO, ValeEstado.CONFIRMADO}) {
            for (Vale v : valeRepository.findByEstadoOrderByFechaDesc(est)) {
                if (v.getFuncionario() == null) continue;
                acumular(acc, v.getFuncionario(), v.getMonto());
            }
        }
        for (Prestamo p : prestamoRepository.findByEstadoOrderByFechaInicioDesc(PrestamoEstado.ACTIVO)) {
            if (p.getFuncionario() == null) continue;
            BigDecimal total = p.getMontoTotal() != null ? p.getMontoTotal() : BigDecimal.ZERO;
            BigDecimal pagado = p.getMontoPagado() != null ? p.getMontoPagado() : BigDecimal.ZERO;
            acumular(acc, p.getFuncionario(), total.subtract(pagado).max(BigDecimal.ZERO));
        }
        return topN(acc, limite);
    }

    /** Top funcionarios por monto de horas extra del mes del período. */
    @Transactional(readOnly = true)
    public List<RrhhRankingItemDto> getTopHorasExtra(String periodo, int limite) {
        validarPeriodo(periodo);
        YearMonth ym = YearMonth.parse(periodo);
        Map<Long, RrhhRankingItemDto> acc = new LinkedHashMap<>();
        for (HoraExtra x : horaExtraRepository.findByFechaBetweenAndAnuladaFalse(ym.atDay(1), ym.atEndOfMonth())) {
            if (x.getFuncionario() == null) continue;
            acumular(acc, x.getFuncionario(), x.getMontoCalculado());
        }
        return topN(acc, limite);
    }

    /** Cumpleaños de funcionarios activos dentro del mes del período. */
    @Transactional(readOnly = true)
    public List<RrhhCumpleanosDto> getCumpleanosDelMes(String periodo) {
        validarPeriodo(periodo);
        int mes = YearMonth.parse(periodo).getMonthValue();
        List<RrhhCumpleanosDto> lista = new ArrayList<>();
        for (Funcionario f : funcionarioService.findAll2()) {
            if (!Boolean.TRUE.equals(f.getActivo()) || f.getPersona() == null
                    || f.getPersona().getNacimiento() == null) continue;
            if (f.getPersona().getNacimiento().getMonthValue() != mes) continue;
            lista.add(new RrhhCumpleanosDto(
                    f.getId(),
                    nombre(f),
                    f.getPersona().getNacimiento().getDayOfMonth(),
                    f.getCargo() != null ? f.getCargo().getNombre() : null));
        }
        return lista.stream()
                .sorted(Comparator.comparing(RrhhCumpleanosDto::getDia))
                .collect(Collectors.toList());
    }

    /**
     * Funcionarios activos con legajo incompleto, peores primero (menor score),
     * paginado. score 1..10 según cuántos de los campos clave estén cargados.
     * Pensado para que el usuario abra el legajo y complete lo que falta.
     */
    @Transactional(readOnly = true)
    public RrhhIncompletosPageDto getFuncionariosIncompletos(int page, int size) {
        List<RrhhFuncionarioIncompletoDto> todos = new ArrayList<>();
        for (Funcionario f : funcionarioService.findAll2()) {
            // Solo funcionarios vigentes: activos y sin egreso registrado.
            if (!Boolean.TRUE.equals(f.getActivo()) || f.getFechaEgreso() != null) continue;
            RrhhFuncionarioIncompletoDto d = evaluarCompletitud(f);
            if (d != null) todos.add(d);
        }
        todos.sort(Comparator.comparing(RrhhFuncionarioIncompletoDto::getScore)
                .thenComparing(d -> d.getNombre() != null ? d.getNombre() : ""));

        int total = todos.size();
        int desde = Math.max(0, page) * Math.max(1, size);
        int hasta = Math.min(total, desde + Math.max(1, size));
        List<RrhhFuncionarioIncompletoDto> items = desde >= total
                ? new ArrayList<>() : todos.subList(desde, hasta);
        return new RrhhIncompletosPageDto(total, new ArrayList<>(items));
    }

    /**
     * Evalúa 8 campos clave del legajo. Devuelve null si está completo (nada que
     * hacer); si no, arma el DTO con score 1..10 y la lista de faltantes.
     */
    private RrhhFuncionarioIncompletoDto evaluarCompletitud(Funcionario f) {
        com.franco.dev.domain.personas.Persona p = f.getPersona();
        List<String> faltantes = new ArrayList<>();
        if (f.getCargo() == null) faltantes.add("Cargo");
        if (f.getSueldo() == null || f.getSueldo().signum() <= 0) faltantes.add("Salario");
        if (f.getFechaIngreso() == null) faltantes.add("Fecha ingreso");
        if (blank(p == null ? null : p.getDocumento())) faltantes.add("Documento");
        if (p == null || p.getNacimiento() == null) faltantes.add("Nacimiento");
        if (blank(p == null ? null : p.getSexo())) faltantes.add("Sexo");
        if (blank(p == null ? null : p.getDireccion())) faltantes.add("Dirección");
        if (blank(p == null ? null : p.getTelefono())) faltantes.add("Teléfono");

        final int TOTAL = 8;
        int cumplidos = TOTAL - faltantes.size();
        if (cumplidos >= TOTAL) return null; // completo

        int score = Math.max(1, (int) Math.round((cumplidos / (double) TOTAL) * 10));
        return new RrhhFuncionarioIncompletoDto(
                f.getId(), nombre(f),
                f.getCargo() != null ? f.getCargo().getNombre() : null,
                score, String.join(", ", faltantes));
    }

    private boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ===== helpers =====

    private void acumular(Map<Long, RrhhRankingItemDto> acc, Funcionario f, BigDecimal monto) {
        if (monto == null) monto = BigDecimal.ZERO;
        RrhhRankingItemDto item = acc.get(f.getId());
        if (item == null) {
            acc.put(f.getId(), new RrhhRankingItemDto(f.getId(), nombre(f), monto, 1L));
        } else {
            item.setPrincipal(item.getPrincipal().add(monto));
            item.setSecundario(item.getSecundario() + 1);
        }
    }

    private List<RrhhRankingItemDto> topN(Map<Long, RrhhRankingItemDto> acc, int limite) {
        return acc.values().stream()
                .sorted(Comparator.comparing(RrhhRankingItemDto::getPrincipal).reversed())
                .limit(Math.max(1, limite))
                .collect(Collectors.toList());
    }

    private String nombre(Funcionario f) {
        if (f.getPersona() == null) return "—";
        String n = f.getPersona().getNombre();
        return n != null && !n.isEmpty() ? n : "—";
    }

    private void validarPeriodo(String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}-\\d{2}")) {
            throw new GraphQLException("Periodo invalido, se espera 'YYYY-MM'");
        }
    }
}
