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
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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
}
