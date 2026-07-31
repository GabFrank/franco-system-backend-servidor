package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.Penalizacion;
import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.PrestamoCuota;
import com.franco.dev.domain.rrhh.Vacacion;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.graphql.rrhh.input.LiquidacionFinalGenerarInput;
import com.franco.dev.service.rrhh.dto.LiquidacionFinalPreview;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalConcepto;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.domain.rrhh.enums.MotivoEgreso;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.repository.rrhh.PenalizacionRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalRepository;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import com.franco.dev.repository.rrhh.PrestamoCuotaRepository;
import com.franco.dev.repository.rrhh.PrestamoRepository;
import com.franco.dev.repository.rrhh.VacacionRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.personas.ClienteService;
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

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

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
    private final MonedaService monedaService;
    private final UsuarioService usuarioService;
    private final ValeRepository valeRepository;
    private final PrestamoRepository prestamoRepository;
    private final PrestamoCuotaRepository prestamoCuotaRepository;
    private final ClienteService clienteService;
    private final VentaCreditoRepository ventaCreditoRepository;
    private final PenalizacionRepository penalizacionRepository;
    private final CreditoConvenioService creditoConvenioService;
    private final com.franco.dev.repository.rrhh.AguinaldoRepository aguinaldoRepository;

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
    public LiquidacionFinal generarBorrador(LiquidacionFinalGenerarInput in) {
        if (in == null || in.getFuncionarioId() == null) throw new GraphQLException("Datos insuficientes para generar el finiquito");
        Long funcionarioId = in.getFuncionarioId();
        MotivoEgreso motivo = in.getMotivoEgreso();
        Funcionario f = funcionarioService.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado"));

        // Fecha de ingreso: override del diálogo o la del funcionario.
        LocalDate ingreso = in.getFechaIngreso() != null ? parseLocalDate(in.getFechaIngreso())
                : (f.getFechaIngreso() != null ? f.getFechaIngreso().toLocalDate() : null);
        LocalDate egreso = in.getFechaEgreso() != null ? parseLocalDate(in.getFechaEgreso())
                : (f.getFechaEgreso() != null ? f.getFechaEgreso().toLocalDate() : LocalDate.now());

        // Overrides opcionales del diálogo (antes de generar). Si no vienen, se auto-calculan.
        BigDecimal salarioPromedio = in.getSalarioBase() != null ? in.getSalarioBase() : calcularSalarioPromedio(f);
        int diasNoGozados = in.getDiasVacaciones() != null ? Math.max(0, in.getDiasVacaciones()) : calcularDiasVacacionesNoGozadas(funcionarioId);
        BigDecimal aguinaldoProporcional = in.getAguinaldo() != null ? in.getAguinaldo() : calcularAguinaldoProporcional(f, egreso);
        BigDecimal diasPorAnio = configuracionRrhhService.getNumber("INDEMNIZACION_DIAS_POR_ANIO", new BigDecimal("15"));
        int minDiasIndemnizacion = configuracionRrhhService.getNumber("INDEMNIZACION_ANTIGUEDAD_MIN_DIAS", new BigDecimal("90")).intValue();
        int diasMes = configuracionRrhhService.getNumber("DIAS_MES_PROMEDIO", new BigDecimal("30")).intValue();
        int diasAnio = configuracionRrhhService.getNumber("DIAS_ANIO_ANTIGUEDAD", new BigDecimal("365")).intValue();

        // Preaviso: días override o según tramo de antigüedad (configurable).
        int aniosAnt = LiquidacionFinalCalculator.antiguedad(ingreso, egreso, diasMes, diasAnio).getAnios();
        int preavisoDias = in.getPreavisoDias() != null ? Math.max(0, in.getPreavisoDias()) : diasPreavisoPorTramo(aniosAnt);
        boolean otorgado = Boolean.TRUE.equals(in.getPreavisoOtorgado());

        LiquidacionFinalCalculator.Resultado r = LiquidacionFinalCalculator.calcular(
                ingreso, egreso, motivo, salarioPromedio, diasNoGozados, aguinaldoProporcional, diasPorAnio,
                minDiasIndemnizacion, diasMes, diasAnio, preavisoDias, otorgado);

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
        lf.setPreavisoOtorgado(otorgado);
        lf.setPreavisoDias(r.isPreavisoAplica() ? r.getPreavisoDias() : preavisoDias);
        lf.setPreavisoMonto(r.getPreavisoMonto());
        lf.setPreavisoEsDescuento(r.isPreavisoEsDescuento());
        lf.setTotalLiquidado(r.getTotalLiquidado());
        if (in.getMonedaId() != null) lf.setMoneda(monedaService.findById(in.getMonedaId()).orElse(null));
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
        // Salario del mes (días trabajados no liquidados) — base sueldo mensual del funcionario.
        BigDecimal sueldoMensual = f.getSueldo() != null ? new BigDecimal(f.getSueldo().toString()) : BigDecimal.ZERO;
        int diasTrabajadosMes = in.getDiasTrabajadosMes() != null ? Math.max(0, in.getDiasTrabajadosMes()) : egreso.getDayOfMonth();
        BigDecimal salarioDelMes = salarioPorDiasTrabajados(sueldoMensual, diasTrabajadosMes, diasMes);
        if (salarioDelMes.signum() > 0) {
            items.add(item(lf, LiquidacionFinalConcepto.SALARIO_MES,
                    "SALARIO DEL MES (" + diasTrabajadosMes + " DIAS TRABAJADOS)", salarioDelMes));
        }
        if (r.isIndemnizacionAplica() && r.getIndemnizacionMonto().signum() > 0) {
            items.add(item(lf, LiquidacionFinalConcepto.INDEMNIZACION,
                    "INDEMNIZACION POR DESPIDO INJUSTIFICADO", r.getIndemnizacionMonto()));
        }
        // Preaviso HABER (despido injustificado sin preaviso otorgado).
        if (r.isPreavisoAplica() && !r.isPreavisoEsDescuento() && r.getPreavisoMonto().signum() > 0) {
            items.add(item(lf, LiquidacionFinalConcepto.PREAVISO,
                    "PREAVISO NO OTORGADO (" + r.getPreavisoDias() + " DIAS)", r.getPreavisoMonto()));
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

        // Preaviso DESCUENTO (renuncia sin preaviso otorgado): ½ del tramo.
        if (r.isPreavisoAplica() && r.isPreavisoEsDescuento() && r.getPreavisoMonto().signum() > 0) {
            LiquidacionFinalItem pre = new LiquidacionFinalItem();
            pre.setLiquidacionFinal(lf);
            pre.setConcepto(LiquidacionFinalConcepto.PREAVISO);
            pre.setDescripcion("PREAVISO NO OTORGADO (" + r.getPreavisoDias() + " DIAS, DESCUENTO 1/2)");
            pre.setMonto(r.getPreavisoMonto());
            pre.setTipo(LiquidacionItemTipo.DESCUENTO);
            pre.setManual(false);
            pre.setEditado(false);
            pre.setReferenciaTipo("PREAVISO");
            itemRepository.save(pre);
        }

        // Descuentos automáticos del funcionario (IPS, vales, cuotas, convenio, penalizaciones),
        // cada uno activable/desactivable desde el diálogo (default: todos activos).
        BigDecimal ipsBase = in.getIpsBase() != null ? in.getIpsBase() : salarioPromedio;
        // IPS: override del diálogo, o por defecto según ipsActivo del funcionario.
        boolean descontarIps = in.getDescontarIps() != null ? in.getDescontarIps() : !Boolean.FALSE.equals(f.getIpsActivo());
        boolean cobrarVales = in.getCobrarVales() == null || in.getCobrarVales();
        boolean cobrarConvenios = in.getCobrarConvenios() == null || in.getCobrarConvenios();
        boolean cobrarPrestamos = in.getCobrarPrestamos() == null || in.getCobrarPrestamos();
        boolean descontarPenal = in.getDescontarPenalizaciones() == null || in.getDescontarPenalizaciones();
        agregarDescuentosAutomaticos(lf, f, ipsBase, egreso, descontarIps, cobrarVales, cobrarConvenios, cobrarPrestamos, descontarPenal);

        // El total sigue a los items (fuente única de verdad para la edición negociada).
        recalcularTotal(lf);
        return lf;
    }

    /** Preview de los valores auto-calculados, para precargar el diálogo sin persistir. */
    public LiquidacionFinalPreview previewDefaults(Long funcionarioId, LocalDate fechaEgreso) {
        Funcionario f = funcionarioService.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado"));
        LocalDate ingreso = f.getFechaIngreso() != null ? f.getFechaIngreso().toLocalDate() : null;
        LocalDate egreso = fechaEgreso != null ? fechaEgreso
                : (f.getFechaEgreso() != null ? f.getFechaEgreso().toLocalDate() : LocalDate.now());
        int diasMes = configuracionRrhhService.getNumber("DIAS_MES_PROMEDIO", new BigDecimal("30")).intValue();
        int diasAnio = configuracionRrhhService.getNumber("DIAS_ANIO_ANTIGUEDAD", new BigDecimal("365")).intValue();
        LiquidacionFinalCalculator.Antiguedad ant = LiquidacionFinalCalculator.antiguedad(ingreso, egreso, diasMes, diasAnio);
        BigDecimal salarioPromedio = calcularSalarioPromedio(f);
        BigDecimal aguinaldo = calcularAguinaldoProporcional(f, egreso);
        int diasVac = calcularDiasVacacionesNoGozadas(funcionarioId);
        int preavisoDias = diasPreavisoPorTramo(ant.getAnios());
        // IPS: por defecto se descuenta salvo que el funcionario tenga ipsActivo=false explícito.
        boolean ipsActivo = !Boolean.FALSE.equals(f.getIpsActivo());
        // Salario del mes: días trabajados = día del mes del egreso; base = sueldo mensual.
        BigDecimal sueldoBase = f.getSueldo() != null ? new BigDecimal(f.getSueldo().toString()) : BigDecimal.ZERO;
        int diasTrabajadosMes = egreso.getDayOfMonth();
        BigDecimal salarioDelMes = salarioPorDiasTrabajados(sueldoBase, diasTrabajadosMes, diasMes);
        return new LiquidacionFinalPreview(
                ingreso != null ? ingreso.toString() : null,
                ant.getAnios(), (int) ant.getDias(), salarioPromedio, sueldoBase, diasTrabajadosMes, salarioDelMes,
                aguinaldo, diasVac, preavisoDias, salarioPromedio, ipsActivo);
    }

    /** Salario por días trabajados: (sueldo / diasMes) × días, guaraníes enteros. */
    private BigDecimal salarioPorDiasTrabajados(BigDecimal sueldo, int dias, int diasMes) {
        if (sueldo == null || dias <= 0) return BigDecimal.ZERO;
        return sueldo.multiply(new BigDecimal(dias))
                .divide(new BigDecimal(diasMes > 0 ? diasMes : 30), 0, RoundingMode.HALF_UP);
    }

    private LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) return null;
        java.time.LocalDateTime d = stringToDate(s);
        return d != null ? d.toLocalDate() : null;
    }

    /** Días de preaviso según tramo de antigüedad (configurables). */
    private int diasPreavisoPorTramo(int anios) {
        if (anios < 1) return configuracionRrhhService.getNumber("PREAVISO_DIAS_HASTA_1A", new BigDecimal("30")).intValue();
        if (anios < 5) return configuracionRrhhService.getNumber("PREAVISO_DIAS_1_5A", new BigDecimal("45")).intValue();
        if (anios < 10) return configuracionRrhhService.getNumber("PREAVISO_DIAS_5_10A", new BigDecimal("60")).intValue();
        return configuracionRrhhService.getNumber("PREAVISO_DIAS_MAS_10A", new BigDecimal("90")).intValue();
    }

    private LiquidacionFinalItem item(LiquidacionFinal lf, LiquidacionFinalConcepto concepto, String desc, BigDecimal monto) {
        LiquidacionFinalItem it = new LiquidacionFinalItem();
        it.setLiquidacionFinal(lf);
        it.setConcepto(concepto);
        it.setDescripcion(desc);
        it.setMonto(monto);
        it.setTipo(LiquidacionItemTipo.HABER);
        it.setManual(false);
        it.setEditado(false);
        return it;
    }

    /** Ítem DESCUENTO automático (obligaciones del funcionario). refTipo/refId permiten saldar al pagar. */
    private LiquidacionFinalItem descItem(LiquidacionFinal lf, String desc, BigDecimal monto, Long refId, String refTipo) {
        LiquidacionFinalItem it = new LiquidacionFinalItem();
        it.setLiquidacionFinal(lf);
        it.setConcepto(LiquidacionFinalConcepto.MANUAL);
        it.setDescripcion(desc);
        it.setMonto(monto);
        it.setTipo(LiquidacionItemTipo.DESCUENTO);
        it.setManual(false);
        it.setEditado(false);
        it.setReferenciaId(refId);
        it.setReferenciaTipo(refTipo);
        return it;
    }

    /**
     * Descuentos automáticos del funcionario al salir (mismas obligaciones que la
     * liquidación mensual): IPS (sobre ipsBase), vales/adelantos, cuotas de préstamo
     * pendientes, crédito por convenio y penalizaciones. Cada bloque es activable
     * desde el diálogo. Se agregan como ítems DESCUENTO editables; el total los resta
     * vía recalcularTotal().
     */
    private void agregarDescuentosAutomaticos(LiquidacionFinal lf, Funcionario f, BigDecimal ipsBase, LocalDate egreso,
                                              boolean descontarIps, boolean cobrarVales, boolean cobrarConvenios,
                                              boolean cobrarPrestamos, boolean descontarPenalizaciones) {
        Long fid = f.getId();

        // IPS (parte del funcionario) — % sobre la base indicada (default salario promedio).
        // Solo si corresponde (funcionario con IPS o forzado desde el diálogo).
        if (descontarIps) {
            BigDecimal ipsPct = configuracionRrhhService.getNumber("IPS_PORCENTAJE_FUNCIONARIO", new BigDecimal("9"));
            BigDecimal base = ipsBase != null ? ipsBase : BigDecimal.ZERO;
            BigDecimal ips = base.multiply(ipsPct).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
            if (ips.signum() > 0) {
                itemRepository.save(descItem(lf, "DESCUENTO IPS", ips, null, "IPS"));
            }
        }

        // Vales / adelantos CONFIRMADO sin liquidar.
        if (cobrarVales) {
            for (Vale v : valeRepository.findByFuncionarioIdAndEstado(fid, ValeEstado.CONFIRMADO)) {
                BigDecimal monto = v.getMonto() != null ? v.getMonto() : BigDecimal.ZERO;
                if (monto.signum() <= 0) continue;
                if (Boolean.TRUE.equals(v.getEsAdelanto())) {
                    itemRepository.save(descItem(lf, "ADELANTO DE SUELDO", monto, v.getId(), "ADELANTO"));
                } else {
                    itemRepository.save(descItem(lf, "VALE", monto, v.getId(), "VALE"));
                }
            }
        }

        // Cuotas de préstamo pendientes/parciales/vencidas.
        if (cobrarPrestamos) {
            for (Prestamo pr : prestamoRepository.findByFuncionarioIdOrderByFechaInicioDesc(fid)) {
                for (PrestamoCuota c : prestamoCuotaRepository.findByPrestamoIdOrderByNumeroAsc(pr.getId())) {
                    boolean pendiente = c.getEstado() == PrestamoCuotaEstado.PENDIENTE
                            || c.getEstado() == PrestamoCuotaEstado.PARCIAL
                            || c.getEstado() == PrestamoCuotaEstado.VENCIDA;
                    if (!pendiente) continue;
                    BigDecimal monto = c.getMonto() != null ? c.getMonto() : BigDecimal.ZERO;
                    BigDecimal pagado = c.getMontoPagado() != null ? c.getMontoPagado() : BigDecimal.ZERO;
                    BigDecimal pend = monto.subtract(pagado);
                    if (pend.signum() <= 0) continue;
                    itemRepository.save(descItem(lf, "CUOTA #" + c.getNumero() + " PRESTAMO #" + pr.getId(),
                            pend, c.getId(), "CPP_CUOTA"));
                }
            }
        }

        // Penalizaciones no anuladas del mes de egreso.
        if (descontarPenalizaciones && egreso != null) {
            LocalDate inicioMes = egreso.withDayOfMonth(1);
            BigDecimal totalPenal = BigDecimal.ZERO;
            for (Penalizacion p : penalizacionRepository.findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(fid, inicioMes, egreso)) {
                if (p.getMonto() != null) totalPenal = totalPenal.add(p.getMonto());
            }
            if (totalPenal.signum() > 0) {
                itemRepository.save(descItem(lf, "PENALIZACIONES DEL MES", totalPenal, null, "PENALIZACION"));
            }
        }

        // Crédito por convenio (compras a crédito) — cuotas impagas del funcionario,
        // cobradas por cuota (arregla el sobre-cobro de usar saldoTotal, que nunca baja)
        // AL FINAL y con tope por disponible (no deja el neto en negativo). El empleado
        // se va → se toman TODAS las cuotas impagas (no solo las vencidas). Al pagar, si
        // la venta queda 100% saldada se la marca FINALIZADO (reversible al anular).
        if (cobrarConvenios) {
            BigDecimal disponible = disponibleFiniquito(lf);
            Long personaId = f.getPersona() != null ? f.getPersona().getId() : null;
            for (CreditoConvenioService.CobroCuota cc :
                    creditoConvenioService.planificar(personaId, null, disponible, null, lf.getId())) {
                String desc = "CUOTA CREDITO - venta #" + (cc.ventaCredito != null ? cc.ventaCredito.getId() : "?")
                        + (cc.parcial ? " (parcial)" : "");
                LiquidacionFinalItem it = descItem(lf, desc, cc.monto, cc.cuota.getId(), "CREDITO_CONVENIO_CUOTA");
                it.setReferenciaSucursalId(cc.cuota.getSucursalId());
                it.setReferenciaEstadoPrevio(cc.ventaCredito != null && cc.ventaCredito.getEstado() != null
                        ? cc.ventaCredito.getEstado().name() : null);
                itemRepository.save(it);
            }
        }
    }

    /** Neto disponible para cobrar convenio = Σ HABER − Σ DESCUENTO de los items ya persistidos. */
    private BigDecimal disponibleFiniquito(LiquidacionFinal lf) {
        BigDecimal haberes = BigDecimal.ZERO;
        BigDecimal descuentos = BigDecimal.ZERO;
        for (LiquidacionFinalItem it : itemRepository.findByLiquidacionFinalIdOrderByIdAsc(lf.getId())) {
            BigDecimal m = it.getMonto() != null ? it.getMonto() : BigDecimal.ZERO;
            if (it.getTipo() == LiquidacionItemTipo.HABER) haberes = haberes.add(m);
            else if (it.getTipo() == LiquidacionItemTipo.DESCUENTO) descuentos = descuentos.add(m);
        }
        BigDecimal disp = haberes.subtract(descuentos);
        return disp.signum() > 0 ? disp : BigDecimal.ZERO;
    }

    // =====================================================================
    // Items editables — "todo es negociable". El total pasa a ser Σ haberes −
    // Σ descuentos de los items; el desglose calculado queda como referencia.
    // =====================================================================

    /** total_liquidado = Σ HABER − Σ DESCUENTO de los items del finiquito. */
    private void recalcularTotal(LiquidacionFinal lf) {
        BigDecimal haberes = BigDecimal.ZERO;
        BigDecimal descuentos = BigDecimal.ZERO;
        for (LiquidacionFinalItem it : itemRepository.findByLiquidacionFinalIdOrderByIdAsc(lf.getId())) {
            BigDecimal m = it.getMonto() != null ? it.getMonto() : BigDecimal.ZERO;
            if (it.getTipo() == LiquidacionItemTipo.DESCUENTO) descuentos = descuentos.add(m);
            else haberes = haberes.add(m);
        }
        lf.setTotalLiquidado(haberes.subtract(descuentos));
        repository.save(lf);
    }

    private LiquidacionFinal borradorDelItem(LiquidacionFinalItem it) {
        LiquidacionFinal lf = it.getLiquidacionFinal();
        if (lf == null) throw new GraphQLException("Item sin liquidación asociada");
        if (lf.getEstado() != LiquidacionFinalEstado.BORRADOR)
            throw new GraphQLException("Solo se pueden modificar items en estado BORRADOR");
        return lf;
    }

    @Transactional
    public LiquidacionFinalItem agregarItemManual(Long liquidacionFinalId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo) {
        LiquidacionFinal lf = repository.findById(liquidacionFinalId)
                .orElseThrow(() -> new GraphQLException("Liquidación final no encontrada"));
        if (lf.getEstado() != LiquidacionFinalEstado.BORRADOR)
            throw new GraphQLException("Solo se pueden agregar items en estado BORRADOR");
        LiquidacionFinalItem it = new LiquidacionFinalItem();
        it.setLiquidacionFinal(lf);
        it.setConcepto(LiquidacionFinalConcepto.MANUAL);
        it.setDescripcion(descripcion != null ? descripcion.toUpperCase() : null);
        it.setMonto(monto != null ? monto : BigDecimal.ZERO);
        it.setTipo(tipo != null ? tipo : LiquidacionItemTipo.HABER);
        it.setManual(true);
        it.setEditado(false);
        it = itemRepository.save(it);
        recalcularTotal(lf);
        return it;
    }

    @Transactional
    public LiquidacionFinalItem editarItem(Long itemId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo, Long usuarioId) {
        LiquidacionFinalItem it = itemRepository.findById(itemId)
                .orElseThrow(() -> new GraphQLException("Item no encontrado"));
        LiquidacionFinal lf = borradorDelItem(it);
        // Guardar el monto original en la primera edición (delta negociado).
        if (!Boolean.TRUE.equals(it.getEditado())) it.setMontoOriginal(it.getMonto());
        if (descripcion != null) it.setDescripcion(descripcion.toUpperCase());
        if (monto != null) it.setMonto(monto);
        if (tipo != null) it.setTipo(tipo);
        it.setEditado(true);
        it.setEditadoEn(LocalDateTime.now());
        if (usuarioId != null) it.setEditadoPor(usuarioService.findById(usuarioId).orElse(null));
        it = itemRepository.save(it);
        recalcularTotal(lf);
        return it;
    }

    @Transactional
    public Boolean eliminarItem(Long itemId) {
        LiquidacionFinalItem it = itemRepository.findById(itemId).orElse(null);
        if (it == null) return false;
        LiquidacionFinal lf = borradorDelItem(it);
        itemRepository.deleteById(itemId);
        recalcularTotal(lf);
        return true;
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
        return suma.divide(new BigDecimal(haberes.size()), 0, RoundingMode.HALF_UP);
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
        BigDecimal proporcional;
        if (hay) {
            proporcional = suma.divide(new BigDecimal("12"), 0, RoundingMode.HALF_UP);
        } else {
            // fallback: sueldo × meses trabajados en el año / 12
            BigDecimal sueldo = f.getSueldo() != null ? new BigDecimal(f.getSueldo().toString()) : BigDecimal.ZERO;
            int mesesTrabajados = egreso.getMonthValue();
            if (f.getFechaIngreso() != null && f.getFechaIngreso().getYear() == anio) {
                mesesTrabajados = egreso.getMonthValue() - f.getFechaIngreso().getMonthValue() + 1;
            }
            mesesTrabajados = Math.max(0, mesesTrabajados);
            proporcional = sueldo.multiply(new BigDecimal(mesesTrabajados)).divide(new BigDecimal("12"), 0, RoundingMode.HALF_UP);
        }
        // Restar el aguinaldo del año YA PAGADO (por separado con AguinaldoService.pagar, o
        // dentro de la liquidacion mensual del mes de aguinaldo), para no pagarlo dos veces
        // en el finiquito. Si ya se pago igual o mas que el proporcional, no queda saldo.
        BigDecimal yaPagado = aguinaldoRepository.findByFuncionarioIdAndAnio(f.getId(), anio)
                .filter(a -> a.getEstado() == com.franco.dev.domain.rrhh.enums.AguinaldoEstado.PAGADO)
                .map(a -> a.getMontoCalculado() != null ? a.getMontoCalculado() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
        return proporcional.subtract(yaPagado).max(BigDecimal.ZERO);
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
     * funcionario.activo=false.
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
        mov.setOrigenTipo(OrigenMovimientoTipo.RRHH_LIQUIDACION_FINAL);
        mov.setOrigenId(lf.getId());
        mov.setDescripcion("LIQUIDACION FINAL - FUNC #" + (lf.getFuncionario() != null ? lf.getFuncionario().getId() : "") + " - LIQF #" + lf.getId());
        mov.setActivo(true);
        mov = movimientoCajaVirtualService.registrarMovimiento(mov);
        lf.setCajaVirtualId(cajaVirtualId);
        lf.setMovimientoCajaVirtualId(mov.getId());

        // Hasta 2026-07 aca se escribia un MovimientoPersonas. Se desvinculo: la tabla
        // resulto write-only (nadie la lee) y mezclaba criterios de signo. Ver issue #159.

        // egreso definitivo
        Funcionario f = lf.getFuncionario();
        if (f != null) {
            f.setActivo(false);
            if (f.getFechaEgreso() == null && lf.getFechaEgreso() != null)
                f.setFechaEgreso(lf.getFechaEgreso().atStartOfDay());
            funcionarioService.save(f);
        }

        // Saldar las obligaciones descontadas (vales DESCONTADO, cuotas PAGADA).
        aplicarEfectosCruzados(lf, true);

        lf.setEstado(LiquidacionFinalEstado.PAGADA);
        lf.setFechaPago(LocalDateTime.now());
        return repository.save(lf);
    }

    /**
     * Aplica (pagar=true) o revierte (pagar=false) el saldado de las obligaciones
     * descontadas en el finiquito, vía la referencia de cada ítem. Espejo de la
     * mensual. El crédito por convenio no se salda acá (reconciliación aparte).
     */
    private void aplicarEfectosCruzados(LiquidacionFinal lf, boolean pagar) {
        for (LiquidacionFinalItem it : itemRepository.findByLiquidacionFinalIdOrderByIdAsc(lf.getId())) {
            String tipo = it.getReferenciaTipo();
            Long refId = it.getReferenciaId();
            if (tipo == null || refId == null) continue;
            switch (tipo) {
                case "VALE":
                case "ADELANTO":
                    valeRepository.findById(refId).ifPresent(v -> {
                        v.setEstado(pagar ? ValeEstado.DESCONTADO : ValeEstado.CONFIRMADO);
                        valeRepository.save(v);
                    });
                    break;
                case "CPP_CUOTA":
                    prestamoCuotaRepository.findById(refId).ifPresent(c -> {
                        BigDecimal monto = it.getMonto() != null ? it.getMonto() : BigDecimal.ZERO;
                        BigDecimal pagado = c.getMontoPagado() != null ? c.getMontoPagado() : BigDecimal.ZERO;
                        c.setMontoPagado(pagar ? pagado.add(monto) : pagado.subtract(monto).max(BigDecimal.ZERO));
                        BigDecimal totalCuota = c.getMonto() != null ? c.getMonto() : BigDecimal.ZERO;
                        c.setEstado(c.getMontoPagado().compareTo(totalCuota) >= 0
                                ? PrestamoCuotaEstado.PAGADA : PrestamoCuotaEstado.PENDIENTE);
                        prestamoCuotaRepository.save(c);
                    });
                    break;
                case "CREDITO_CONVENIO_CUOTA":
                    // El cobro ya quedo registrado por el item. Reconciliamos el estado del
                    // VentaCredito: FINALIZADO si quedo 100% saldado; al anular, se restaura
                    // el estado previo (excluyendo este finiquito del calculo del remanente).
                    creditoConvenioService.reconciliarPorCuota(refId, it.getReferenciaSucursalId(),
                            pagar ? null : it.getReferenciaEstadoPrevio(),
                            null, pagar ? null : lf.getId());
                    break;
                default:
                    break;
            }
        }
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
            rev.setOrigenTipo(OrigenMovimientoTipo.RRHH_LIQUIDACION_FINAL);
            rev.setOrigenId(lf.getId());
            rev.setDescripcion("ANULACION LIQUIDACION FINAL #" + lf.getId());
            rev.setActivo(true);
            movimientoCajaVirtualService.registrarMovimiento(rev);
            // Revertir el saldado de vales/cuotas.
            aplicarEfectosCruzados(lf, false);
        }
        lf.setEstado(LiquidacionFinalEstado.ANULADA);
        return repository.save(lf);
    }
}
