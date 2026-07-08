package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.MovimientoPersonas;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.TipoMovimientoPersonas;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.Vacacion;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalConcepto;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.domain.rrhh.enums.MotivoEgreso;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalRepository;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import com.franco.dev.repository.rrhh.VacacionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.financiero.MovimientoPersonasService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.builder.LiquidacionFinalCalculator;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class LiquidacionFinalService extends CrudService<LiquidacionFinal, LiquidacionFinalRepository, Long> {

    private final LiquidacionFinalRepository repository;
    private final LiquidacionFinalItemRepository itemRepository;
    private final FuncionarioService funcionarioService;
    private final LiquidacionSueldoRepository liquidacionSueldoRepository;
    private final VacacionRepository vacacionRepository;
    private final ConfiguracionRrhhService configuracionRrhhService;
    private final CajaVirtualService cajaVirtualService;
    private final MovimientoCajaVirtualService movimientoCajaVirtualService;
    private final MovimientoPersonasService movimientoPersonasService;
    private final MonedaService monedaService;
    private final UsuarioService usuarioService;

    @Override
    public LiquidacionFinalRepository getRepository() {
        return repository;
    }

    public List<LiquidacionFinal> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByCreadoEnDesc(funcionarioId);
    }

    public List<LiquidacionFinalItem> findItems(Long liquidacionFinalId) {
        return itemRepository.findByLiquidacionFinalIdOrderByIdAsc(liquidacionFinalId);
    }

    /**
     * Genera (o regenera) el borrador de finiquito de un funcionario. Idempotente:
     * si ya existe un BORRADOR lo reusa; nunca toca APROBADA/PAGADA.
     */
    @Transactional
    public LiquidacionFinal generarBorrador(Long funcionarioId, MotivoEgreso motivo, LocalDate fechaEgreso, Long monedaId) {
        Funcionario f = funcionarioService.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado"));

        LocalDate ingreso = f.getFechaIngreso() != null ? f.getFechaIngreso().toLocalDate() : null;
        LocalDate egreso = fechaEgreso != null ? fechaEgreso
                : (f.getFechaEgreso() != null ? f.getFechaEgreso().toLocalDate() : LocalDate.now());

        BigDecimal salarioPromedio = calcularSalarioPromedio(f);
        int diasNoGozados = calcularDiasVacacionesNoGozadas(funcionarioId);
        BigDecimal aguinaldoProporcional = calcularAguinaldoProporcional(f, egreso);
        BigDecimal diasPorAnio = configuracionRrhhService.getNumber("INDEMNIZACION_DIAS_POR_ANIO", new BigDecimal("15"));
        int minDiasIndemnizacion = configuracionRrhhService.getNumber("INDEMNIZACION_ANTIGUEDAD_MIN_DIAS", new BigDecimal("90")).intValue();
        int diasMes = configuracionRrhhService.getNumber("DIAS_MES_PROMEDIO", new BigDecimal("30")).intValue();
        int diasAnio = configuracionRrhhService.getNumber("DIAS_ANIO_ANTIGUEDAD", new BigDecimal("365")).intValue();

        LiquidacionFinalCalculator.Resultado r = LiquidacionFinalCalculator.calcular(
                ingreso, egreso, motivo, salarioPromedio, diasNoGozados, aguinaldoProporcional, diasPorAnio,
                minDiasIndemnizacion, diasMes, diasAnio);

        LiquidacionFinal lf = repository
                .findFirstByFuncionarioIdAndEstadoOrderByCreadoEnDesc(funcionarioId, LiquidacionFinalEstado.BORRADOR)
                .orElseGet(LiquidacionFinal::new);

        lf.setFuncionario(f);
        lf.setFechaEgreso(egreso);
        lf.setMotivoEgreso(motivo);
        lf.setAntiguedadDias((int) r.getAntiguedad().getDias());
        lf.setAntiguedadMeses(r.getAntiguedad().getMeses());
        lf.setAntiguedadAnios(r.getAntiguedad().getAnios());
        lf.setSalarioPromedio(salarioPromedio);
        lf.setIndemnizacionAplica(r.isIndemnizacionAplica());
        lf.setIndemnizacionMonto(r.getIndemnizacionMonto());
        lf.setDiasVacacionesNoGozadas(r.getDiasNoGozados());
        lf.setMontoVacacionesNoGozadas(r.getMontoVacacionesNoGozadas());
        lf.setAguinaldoProporcional(r.getAguinaldoProporcional());
        lf.setTotalLiquidado(r.getTotalLiquidado());
        if (monedaId != null) lf.setMoneda(monedaService.findById(monedaId).orElse(null));
        else if (lf.getMoneda() == null) lf.setMoneda(f.getMoneda());
        lf.setEstado(LiquidacionFinalEstado.BORRADOR);
        if (lf.getCreadoEn() == null) lf.setCreadoEn(LocalDateTime.now());
        lf = repository.save(lf);

        // reconstruir items automáticos
        if (lf.getId() != null) {
            for (LiquidacionFinalItem old : itemRepository.findByLiquidacionFinalIdOrderByIdAsc(lf.getId())) {
                itemRepository.deleteById(old.getId());
            }
        }
        List<LiquidacionFinalItem> items = new ArrayList<>();
        if (r.isIndemnizacionAplica() && r.getIndemnizacionMonto().signum() > 0) {
            items.add(item(lf, LiquidacionFinalConcepto.INDEMNIZACION,
                    "INDEMNIZACION POR DESPIDO INJUSTIFICADO", r.getIndemnizacionMonto()));
        }
        if (r.getMontoVacacionesNoGozadas().signum() > 0) {
            items.add(item(lf, LiquidacionFinalConcepto.VACACIONES_NO_GOZADAS,
                    "VACACIONES NO GOZADAS (" + r.getDiasNoGozados() + " DIAS)", r.getMontoVacacionesNoGozadas()));
        }
        if (r.getAguinaldoProporcional().signum() > 0) {
            items.add(item(lf, LiquidacionFinalConcepto.AGUINALDO_PROPORCIONAL,
                    "AGUINALDO PROPORCIONAL", r.getAguinaldoProporcional()));
        }
        for (LiquidacionFinalItem it : items) itemRepository.save(it);

        return lf;
    }

    private LiquidacionFinalItem item(LiquidacionFinal lf, LiquidacionFinalConcepto concepto, String desc, BigDecimal monto) {
        LiquidacionFinalItem it = new LiquidacionFinalItem();
        it.setLiquidacionFinal(lf);
        it.setConcepto(concepto);
        it.setDescripcion(desc);
        it.setMonto(monto);
        return it;
    }

    /** Promedio de total_haberes de las últimas N liquidaciones APROBADA/PAGADA; fallback al sueldo. */
    private BigDecimal calcularSalarioPromedio(Funcionario f) {
        int mesesPromedio = configuracionRrhhService.getNumber("MESES_PROMEDIO_LIQUIDACION_FINAL", new BigDecimal("6")).intValue();
        if (mesesPromedio < 1) mesesPromedio = 6;
        List<LiquidacionSueldo> liqs = liquidacionSueldoRepository.findByFuncionarioIdOrderByPeriodoDesc(f.getId());
        List<BigDecimal> haberes = new ArrayList<>();
        for (LiquidacionSueldo l : liqs) {
            if (l.getEstado() == LiquidacionSueldoEstado.APROBADA || l.getEstado() == LiquidacionSueldoEstado.PAGADA) {
                if (l.getTotalHaberes() != null) haberes.add(l.getTotalHaberes());
                if (haberes.size() >= mesesPromedio) break;
            }
        }
        if (haberes.isEmpty()) {
            return f.getSueldo() != null ? new BigDecimal(f.getSueldo().toString()) : BigDecimal.ZERO;
        }
        BigDecimal suma = BigDecimal.ZERO;
        for (BigDecimal h : haberes) suma = suma.add(h);
        return suma.divide(new BigDecimal(haberes.size()), 2, RoundingMode.HALF_UP);
    }

    private int calcularDiasVacacionesNoGozadas(Long funcionarioId) {
        int dias = 0;
        for (Vacacion v : vacacionRepository.findByFuncionarioIdAndPrescritaFalse(funcionarioId)) {
            int gen = v.getDiasGenerados() != null ? v.getDiasGenerados() : 0;
            int goz = v.getDiasGozados() != null ? v.getDiasGozados() : 0;
            dias += Math.max(0, gen - goz);
        }
        return dias;
    }

    /** Σ total_haberes de las liquidaciones del año del egreso / 12; fallback sueldo × meses/12. */
    private BigDecimal calcularAguinaldoProporcional(Funcionario f, LocalDate egreso) {
        int anio = egreso.getYear();
        BigDecimal suma = BigDecimal.ZERO;
        boolean hay = false;
        for (LiquidacionSueldo l : liquidacionSueldoRepository.findByFuncionarioIdOrderByPeriodoDesc(f.getId())) {
            if (l.getPeriodo() != null && l.getPeriodo().startsWith(anio + "-")
                    && (l.getEstado() == LiquidacionSueldoEstado.APROBADA || l.getEstado() == LiquidacionSueldoEstado.PAGADA)
                    && l.getTotalHaberes() != null) {
                suma = suma.add(l.getTotalHaberes());
                hay = true;
            }
        }
        if (hay) return suma.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        // fallback: sueldo × meses trabajados en el año / 12
        BigDecimal sueldo = f.getSueldo() != null ? new BigDecimal(f.getSueldo().toString()) : BigDecimal.ZERO;
        int mesesTrabajados = egreso.getMonthValue();
        if (f.getFechaIngreso() != null && f.getFechaIngreso().getYear() == anio) {
            mesesTrabajados = egreso.getMonthValue() - f.getFechaIngreso().getMonthValue() + 1;
        }
        mesesTrabajados = Math.max(0, mesesTrabajados);
        return sueldo.multiply(new BigDecimal(mesesTrabajados)).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public LiquidacionFinal aprobar(Long id, Long aprobadoPorId) {
        LiquidacionFinal lf = repository.findById(id).orElseThrow(() -> new GraphQLException("Liquidacion final no encontrada"));
        if (lf.getEstado() != LiquidacionFinalEstado.BORRADOR) throw new GraphQLException("Solo se aprueba un BORRADOR");
        lf.setEstado(LiquidacionFinalEstado.APROBADA);
        if (aprobadoPorId != null) lf.setAprobadoPor(usuarioService.findById(aprobadoPorId).orElse(null));
        lf.setFechaAprobacion(LocalDateTime.now());
        return repository.save(lf);
    }

    @Transactional
    public LiquidacionFinal volverBorrador(Long id) {
        LiquidacionFinal lf = repository.findById(id).orElseThrow(() -> new GraphQLException("Liquidacion final no encontrada"));
        if (lf.getEstado() != LiquidacionFinalEstado.APROBADA) throw new GraphQLException("Solo una APROBADA vuelve a BORRADOR");
        lf.setEstado(LiquidacionFinalEstado.BORRADOR);
        lf.setAprobadoPor(null);
        lf.setFechaAprobacion(null);
        return repository.save(lf);
    }

    /**
     * Paga el finiquito APROBADO: EGRESO real en la Caja Mayor +
     * MovimientoPersonas(PAGO_SALARIO, obs LIQUIDACION FINAL) + funcionario.activo=false.
     */
    @Transactional
    public LiquidacionFinal pagar(Long id, Long cajaVirtualId) {
        LiquidacionFinal lf = repository.findById(id).orElseThrow(() -> new GraphQLException("Liquidacion final no encontrada"));
        if (lf.getEstado() != LiquidacionFinalEstado.APROBADA) throw new GraphQLException("Solo se paga una APROBADA");

        CajaVirtual caja = cajaVirtualService.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja Mayor no encontrada"));
        MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
        mov.setCajaVirtual(caja);
        mov.setTipoMovimiento(CajaVirtualTipoMovimiento.EGRESO);
        mov.setCantidad(lf.getTotalLiquidado() != null ? lf.getTotalLiquidado().doubleValue() : 0.0);
        mov.setMoneda(lf.getMoneda());
        mov.setReferenciaId(lf.getId());
        mov.setDescripcion("LIQUIDACION FINAL - FUNC #" + (lf.getFuncionario() != null ? lf.getFuncionario().getId() : "") + " - LIQF #" + lf.getId());
        mov.setActivo(true);
        mov = movimientoCajaVirtualService.registrarMovimiento(mov);
        lf.setCajaVirtualId(cajaVirtualId);
        lf.setMovimientoCajaVirtualId(mov.getId());

        if (lf.getFuncionario() != null && lf.getFuncionario().getPersona() != null) {
            MovimientoPersonas mp = new MovimientoPersonas();
            mp.setPersona(lf.getFuncionario().getPersona());
            mp.setTipo(TipoMovimientoPersonas.PAGO_SALARIO);
            mp.setReferenciaId(lf.getId());
            mp.setValorTotal(lf.getTotalLiquidado() != null ? lf.getTotalLiquidado().doubleValue() : 0.0);
            mp.setActivo(true);
            mp.setObservacion("LIQUIDACION FINAL");
            mp = movimientoPersonasService.save(mp);
            lf.setMovimientoPersonaId(mp.getId());
        }

        // egreso definitivo
        Funcionario f = lf.getFuncionario();
        if (f != null) {
            f.setActivo(false);
            if (f.getFechaEgreso() == null && lf.getFechaEgreso() != null)
                f.setFechaEgreso(lf.getFechaEgreso().atStartOfDay());
            funcionarioService.save(f);
        }

        lf.setEstado(LiquidacionFinalEstado.PAGADA);
        lf.setFechaPago(LocalDateTime.now());
        return repository.save(lf);
    }

    /** Anula un finiquito PAGADO: contra-asiento AJUSTE en la caja. */
    @Transactional
    public LiquidacionFinal anular(Long id) {
        LiquidacionFinal lf = repository.findById(id).orElseThrow(() -> new GraphQLException("Liquidacion final no encontrada"));
        if (lf.getEstado() == LiquidacionFinalEstado.ANULADA) return lf;
        if (lf.getEstado() == LiquidacionFinalEstado.PAGADA && lf.getCajaVirtualId() != null) {
            CajaVirtual caja = cajaVirtualService.findById(lf.getCajaVirtualId())
                    .orElseThrow(() -> new GraphQLException("Caja Mayor no encontrada"));
            MovimientoCajaVirtual rev = new MovimientoCajaVirtual();
            rev.setCajaVirtual(caja);
            rev.setTipoMovimiento(CajaVirtualTipoMovimiento.AJUSTE);
            rev.setCantidad(lf.getTotalLiquidado() != null ? lf.getTotalLiquidado().doubleValue() : 0.0);
            rev.setMoneda(lf.getMoneda());
            rev.setReferenciaId(lf.getId());
            rev.setDescripcion("ANULACION LIQUIDACION FINAL #" + lf.getId());
            rev.setActivo(true);
            movimientoCajaVirtualService.registrarMovimiento(rev);
        }
        lf.setEstado(LiquidacionFinalEstado.ANULADA);
        return repository.save(lf);
    }
}
