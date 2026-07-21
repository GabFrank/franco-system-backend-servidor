package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.MovimientoPersonas;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.TipoMovimientoPersonas;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.*;
import com.franco.dev.domain.rrhh.enums.*;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.financiero.MovimientoPersonasService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.builder.LiquidacionCalculator;
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
public class LiquidacionSueldoService extends CrudService<LiquidacionSueldo, LiquidacionSueldoRepository, Long> {

    private final LiquidacionSueldoRepository repository;
    private final LiquidacionItemRepository itemRepository;
    private final FuncionarioService funcionarioService;
    private final MonedaService monedaService;
    private final ConfiguracionRrhhService configuracionRrhhService;
    private final HoraExtraRepository horaExtraRepository;
    private final PenalizacionRepository penalizacionRepository;
    private final JustificativoRepository justificativoRepository;
    private final ValeRepository valeRepository;
    private final BonoRepository bonoRepository;
    private final AguinaldoRepository aguinaldoRepository;
    private final VacacionRepository vacacionRepository;
    private final VacacionVentaRepository vacacionVentaRepository;
    private final PrestamoRepository prestamoRepository;
    private final PrestamoCuotaRepository prestamoCuotaRepository;
    private final CajaVirtualService cajaVirtualService;
    private final MovimientoCajaVirtualService movimientoCajaVirtualService;
    private final MovimientoPersonasService movimientoPersonasService;
    private final UsuarioService usuarioService;
    private final com.franco.dev.service.administrativo.JornadaService jornadaService;

    @Override
    public LiquidacionSueldoRepository getRepository() {
        return repository;
    }

    public List<LiquidacionSueldo> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByPeriodoDesc(funcionarioId);
    }

    public List<LiquidacionSueldo> findByPeriodo(String periodo) {
        return repository.findByPeriodoOrderByIdAsc(periodo);
    }

    public List<LiquidacionItem> findItems(Long liquidacionId) {
        return itemRepository.findByLiquidacionIdOrderByIdAsc(liquidacionId);
    }

    // =====================================================================
    // Generacion del borrador (motor)
    // =====================================================================

    /**
     * Genera (o regenera) el borrador de la liquidacion de un funcionario para
     * un periodo 'YYYY-MM'. Preserva los items manuales; recalcula los
     * automaticos. Espejo del algoritmo de FRC Gourmet.
     */
    @Transactional
    public LiquidacionSueldo generarBorrador(Long funcionarioId, String periodo, Long monedaId) {
        Funcionario f = funcionarioService.findById(funcionarioId).orElse(null);
        if (f == null) throw new GraphQLException("Funcionario no encontrado");
        if (periodo == null || !periodo.matches("\\d{4}-\\d{2}")) {
            throw new GraphQLException("Periodo invalido, se espera 'YYYY-MM'");
        }
        int anio = Integer.parseInt(periodo.substring(0, 4));
        int mes = Integer.parseInt(periodo.substring(5, 7));
        int diaCierre = configuracionRrhhService.getNumber("DIA_CIERRE_MES", new BigDecimal("31")).intValue();
        LocalDate[] rango = calcularRangoPeriodo(anio, mes, diaCierre);
        LocalDate inicio = rango[0];
        LocalDate fin = rango[1];

        LiquidacionSueldo liq = repository.findByFuncionarioIdAndPeriodo(funcionarioId, periodo).orElse(null);
        if (liq != null && (liq.getEstado() == LiquidacionSueldoEstado.APROBADA
                || liq.getEstado() == LiquidacionSueldoEstado.PAGADA)) {
            throw new GraphQLException("La liquidacion ya esta " + liq.getEstado() + ", no se puede regenerar");
        }
        List<LiquidacionItem> manuales = new ArrayList<>();
        if (liq == null) {
            liq = new LiquidacionSueldo();
            liq.setFuncionario(f);
            liq.setPeriodo(periodo);
            liq.setCreadoEn(LocalDateTime.now());
        } else {
            // preservar items manuales, borrar automaticos
            for (LiquidacionItem it : itemRepository.findByLiquidacionIdOrderByIdAsc(liq.getId())) {
                if (Boolean.TRUE.equals(it.getManual())) manuales.add(it);
                else itemRepository.deleteById(it.getId());
            }
        }
        liq.setFechaInicio(inicio);
        liq.setFechaFin(fin);
        liq.setEstado(LiquidacionSueldoEstado.BORRADOR);
        if (monedaId != null) liq.setMoneda(monedaService.findById(monedaId).orElse(null));
        BigDecimal salarioBase = calcularSalarioBase(f, inicio, fin);
        liq.setSalarioBase(salarioBase);
        liq = repository.save(liq);

        List<LiquidacionItem> items = new ArrayList<>(manuales);
        items.addAll(construirItemsAutomaticos(liq, f, salarioBase, inicio, fin, mes));

        // persistir items automaticos y recomputar totales
        for (LiquidacionItem it : items) {
            if (it.getId() == null) {
                it.setLiquidacion(liq);
                itemRepository.save(it);
            }
        }
        aplicarTotales(liq, items);
        return repository.save(liq);
    }

    /**
     * Rango de fechas del período según el día de cierre de nómina configurado
     * (DIA_CIERRE_MES). Si el día de cierre es fin de mes (>=28) el período es el
     * mes calendario. Con un día menor (ej. 25) el período va del día siguiente
     * al cierre del mes anterior (26) al día de cierre del mes actual (25).
     */
    private LocalDate[] calcularRangoPeriodo(int anio, int mes, int diaCierre) {
        java.time.YearMonth ym = java.time.YearMonth.of(anio, mes);
        if (diaCierre >= 28) {
            return new LocalDate[]{ ym.atDay(1), ym.atEndOfMonth() };
        }
        LocalDate fin = ym.atDay(Math.min(diaCierre, ym.lengthOfMonth()));
        java.time.YearMonth prev = ym.minusMonths(1);
        LocalDate inicio = prev.atDay(Math.min(diaCierre + 1, prev.lengthOfMonth()));
        return new LocalDate[]{ inicio, fin };
    }

    /**
     * Salario base del período. Para jornaleros/diaristas: valor_jornal × días
     * trabajados (de las jornadas del período). Para mensuales: el sueldo fijo.
     */
    private BigDecimal calcularSalarioBase(Funcionario f, LocalDate inicio, LocalDate fin) {
        if (Boolean.TRUE.equals(f.getDiarista())) {
            // Las jornadas se registran contra la cuenta de LOGIN del empleado. Ese
            // login se resuelve por la persona compartida, NO por f.getUsuario()
            // (funcionario.usuario_id = "creado_por"), que apuntaria a las jornadas
            // de quien creo el registro y daria un salario base equivocado.
            Usuario login = f.getPersona() != null
                    ? usuarioService.findByPersonaId(f.getPersona().getId()) : null;
            int diasTrabajados = login != null
                    ? jornadaService.findByUsuarioIdAndFechaRange(login.getId(), inicio.toString(), fin.toString()).size()
                    : 0;
            BigDecimal valorJornal = f.getValorJornal() != null ? new BigDecimal(f.getValorJornal().toString()) : BigDecimal.ZERO;
            return com.franco.dev.service.rrhh.builder.JornaleroCalculator.calcularSalarioBase(valorJornal, diasTrabajados);
        }
        return f.getSueldo() != null ? new BigDecimal(f.getSueldo().toString()) : BigDecimal.ZERO;
    }

    private List<LiquidacionItem> construirItemsAutomaticos(LiquidacionSueldo liq, Funcionario f,
                                                            BigDecimal salarioBase, LocalDate inicio,
                                                            LocalDate fin, int mes) {
        List<LiquidacionItem> items = new ArrayList<>();
        Long fid = f.getId();

        // SALARIO_BASE (HABER)
        items.add(item(liq, "SALARIO_BASE", "SALARIO BASE", salarioBase, LiquidacionItemTipo.HABER, null, null));

        // IPS_DESCUENTO (DESC)
        BigDecimal ipsPct = configuracionRrhhService.getNumber("IPS_PORCENTAJE_FUNCIONARIO", new BigDecimal("9"));
        BigDecimal ips = salarioBase.multiply(ipsPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        if (ips.signum() > 0) items.add(item(liq, "IPS_DESCUENTO", "DESCUENTO IPS", ips, LiquidacionItemTipo.DESCUENTO, null, null));

        // HORA_EXTRA (HABER)
        BigDecimal he = BigDecimal.ZERO;
        for (HoraExtra x : horaExtraRepository.findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(fid, inicio, fin)) {
            if (x.getMontoCalculado() != null) he = he.add(x.getMontoCalculado());
        }
        if (he.signum() > 0) items.add(item(liq, "HORA_EXTRA", "HORAS EXTRA", he, LiquidacionItemTipo.HABER, null, null));

        // PENALIZACION (DESC)
        BigDecimal pen = BigDecimal.ZERO;
        for (Penalizacion p : penalizacionRepository.findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(fid, inicio, fin)) {
            if (p.getMonto() != null) pen = pen.add(p.getMonto());
        }
        if (pen.signum() > 0) items.add(item(liq, "PENALIZACION", "PENALIZACIONES", pen, LiquidacionItemTipo.DESCUENTO, null, null));

        // JUSTIFICATIVO_DESCUENTO (DESC) — dias justificados cuyo TIPO descuenta salario.
        // Cuanto descuenta cada tipo lo define el catalogo (MEDIO_DIA / DIA_COMPLETO),
        // asi que agregar un tipo nuevo no requiere tocar este calculo.
        BigDecimal diasMes = configuracionRrhhService.getNumber("DIAS_MES_PROMEDIO", new BigDecimal("30"));
        double diasDescontados = 0.0;
        for (Justificativo x : justificativoRepository
                .findByFuncionarioIdAndFechaBetweenOrderByFechaAsc(fid, inicio, fin)) {
            if (x.getTipo() == null || x.getTipo().getDescuentaSalario() == null) continue;
            diasDescontados += x.getTipo().getDescuentaSalario().factor();
        }
        if (diasDescontados > 0 && diasMes.signum() > 0) {
            BigDecimal salarioDiario = salarioBase.divide(diasMes, 2, RoundingMode.HALF_UP);
            BigDecimal descJust = salarioDiario
                    .multiply(BigDecimal.valueOf(diasDescontados))
                    .setScale(2, RoundingMode.HALF_UP);
            if (descJust.signum() > 0) {
                String detalle = "JUSTIFICATIVOS (" + (diasDescontados == Math.floor(diasDescontados)
                        ? String.valueOf((long) diasDescontados) : String.valueOf(diasDescontados)) + " DIA/S)";
                items.add(item(liq, "JUSTIFICATIVO_DESCUENTO", detalle, descJust, LiquidacionItemTipo.DESCUENTO, null, null));
            }
        }

        // BONO_MANUAL (HABER) — bonos no anulados, sin liquidar, en el periodo
        for (Bono b : bonoRepository.findByFuncionarioIdOrderByFechaDesc(fid)) {
            if (Boolean.TRUE.equals(b.getAnulado())) continue;
            if (b.getLiquidacionId() != null) continue;
            if (b.getFecha() == null || b.getFecha().isBefore(inicio) || b.getFecha().isAfter(fin)) continue;
            items.add(item(liq, "BONO_MANUAL", "BONO " + (b.getTipo() != null ? b.getTipo() : ""),
                    b.getMonto() != null ? b.getMonto() : BigDecimal.ZERO, LiquidacionItemTipo.HABER, b.getId(), "BONO"));
        }

        // VALE_DESCUENTO / ADELANTO_DESCUENTO — vales CONFIRMADO sin liquidar
        for (Vale v : valeRepository.findByFuncionarioIdAndEstado(fid, ValeEstado.CONFIRMADO)) {
            if (v.getLiquidacionId() != null) continue;
            BigDecimal monto = v.getMonto() != null ? v.getMonto() : BigDecimal.ZERO;
            if (Boolean.TRUE.equals(v.getEsAdelanto())) {
                items.add(item(liq, "ADELANTO_DESCUENTO", "ADELANTO DE SUELDO", monto, LiquidacionItemTipo.DESCUENTO, v.getId(), "ADELANTO"));
            } else {
                items.add(item(liq, "VALE_DESCUENTO", "VALE", monto, LiquidacionItemTipo.DESCUENTO, v.getId(), "VALE"));
            }
        }

        // PRESTAMO_CUOTA (DESC) — cuotas pendientes con vencimiento <= fin
        for (Prestamo pr : prestamoRepository.findByFuncionarioIdOrderByFechaInicioDesc(fid)) {
            for (PrestamoCuota c : prestamoCuotaRepository.findByPrestamoIdOrderByNumeroAsc(pr.getId())) {
                boolean pendiente = c.getEstado() == PrestamoCuotaEstado.PENDIENTE
                        || c.getEstado() == PrestamoCuotaEstado.PARCIAL
                        || c.getEstado() == PrestamoCuotaEstado.VENCIDA;
                if (!pendiente) continue;
                if (c.getFechaVencimiento() == null || c.getFechaVencimiento().isAfter(fin)) continue;
                BigDecimal pend = (c.getMonto() != null ? c.getMonto() : BigDecimal.ZERO)
                        .subtract(c.getMontoPagado() != null ? c.getMontoPagado() : BigDecimal.ZERO);
                if (pend.signum() <= 0) continue;
                items.add(item(liq, "PRESTAMO_CUOTA", "CUOTA #" + c.getNumero() + " PRESTAMO #" + pr.getId(),
                        pend, LiquidacionItemTipo.DESCUENTO, c.getId(), "CPP_CUOTA"));
            }
        }

        // AGUINALDO (HABER) — si es el mes de aguinaldo y esta APROBADO
        int mesAguinaldo = configuracionRrhhService.getNumber("MES_AGUINALDO", new BigDecimal("12")).intValue();
        if (mes == mesAguinaldo) {
            Optional<Aguinaldo> ag = aguinaldoRepository.findByFuncionarioIdAndAnio(fid, inicio.getYear());
            if (ag.isPresent() && ag.get().getEstado() == AguinaldoEstado.APROBADO && ag.get().getLiquidacionId() == null) {
                items.add(item(liq, "AGUINALDO", "AGUINALDO", ag.get().getMontoCalculado(), LiquidacionItemTipo.HABER, ag.get().getId(), "AGUINALDO"));
            }
        }

        // VACACION_VENTA (HABER) — ventas de dias PENDIENTE
        for (Vacacion vac : vacacionRepository.findByFuncionarioIdOrderByAnioServicioDesc(fid)) {
            for (VacacionVenta venta : vacacionVentaRepository.findByVacacionIdOrderByFechaDesc(vac.getId())) {
                if (venta.getEstado() == VacacionVentaEstado.PENDIENTE && venta.getLiquidacionId() == null) {
                    items.add(item(liq, "VACACION_VENTA", "VENTA DE VACACIONES", venta.getMonto(), LiquidacionItemTipo.HABER, venta.getId(), "VACACION_VENTA"));
                }
            }
        }
        return items;
    }

    private LiquidacionItem item(LiquidacionSueldo liq, String codigo, String desc, BigDecimal monto,
                                 LiquidacionItemTipo tipo, Long refId, String refTipo) {
        LiquidacionItem it = new LiquidacionItem();
        it.setLiquidacion(liq);
        it.setCodigo(codigo);
        it.setDescripcion(desc);
        it.setMonto(monto != null ? monto : BigDecimal.ZERO);
        it.setTipo(tipo);
        it.setReferenciaId(refId);
        it.setReferenciaTipo(refTipo);
        it.setManual(false);
        return it;
    }

    private void aplicarTotales(LiquidacionSueldo liq, List<LiquidacionItem> items) {
        LiquidacionCalculator.Totales t = LiquidacionCalculator.calcular(items);
        liq.setTotalHaberes(t.getTotalHaberes());
        liq.setTotalDescuentos(t.getTotalDescuentos());
        liq.setTotalNeto(t.getTotalNeto());
    }

    // =====================================================================
    // Items manuales
    // =====================================================================

    @Transactional
    public LiquidacionItem agregarItemManual(Long liquidacionId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo) {
        LiquidacionSueldo liq = repository.findById(liquidacionId)
                .orElseThrow(() -> new GraphQLException("Liquidacion no encontrada"));
        if (liq.getEstado() != LiquidacionSueldoEstado.BORRADOR) {
            throw new GraphQLException("Solo se pueden agregar items en estado BORRADOR");
        }
        LiquidacionItem it = new LiquidacionItem();
        it.setLiquidacion(liq);
        it.setCodigo(tipo == LiquidacionItemTipo.HABER ? "HABER_MANUAL" : "DESCUENTO_MANUAL");
        it.setDescripcion(descripcion != null ? descripcion.toUpperCase() : null);
        it.setMonto(monto != null ? monto : BigDecimal.ZERO);
        it.setTipo(tipo);
        it.setManual(true);
        it = itemRepository.save(it);
        aplicarTotales(liq, itemRepository.findByLiquidacionIdOrderByIdAsc(liquidacionId));
        repository.save(liq);
        return it;
    }

    @Transactional
    public Boolean eliminarItem(Long itemId) {
        LiquidacionItem it = itemRepository.findById(itemId).orElse(null);
        if (it == null) return false;
        LiquidacionSueldo liq = it.getLiquidacion();
        if (liq != null && liq.getEstado() != LiquidacionSueldoEstado.BORRADOR) {
            throw new GraphQLException("Solo se pueden eliminar items en estado BORRADOR");
        }
        itemRepository.deleteById(itemId);
        if (liq != null) {
            aplicarTotales(liq, itemRepository.findByLiquidacionIdOrderByIdAsc(liq.getId()));
            repository.save(liq);
        }
        return true;
    }

    // =====================================================================
    // Estados
    // =====================================================================

    @Transactional
    public LiquidacionSueldo aprobar(Long id, Long aprobadoPorId) {
        LiquidacionSueldo liq = repository.findById(id).orElseThrow(() -> new GraphQLException("Liquidacion no encontrada"));
        if (liq.getEstado() != LiquidacionSueldoEstado.BORRADOR) throw new GraphQLException("Solo se aprueba desde BORRADOR");
        liq.setEstado(LiquidacionSueldoEstado.APROBADA);
        liq.setFechaAprobacion(LocalDateTime.now());
        if (aprobadoPorId != null) liq.setAprobadoPor(usuarioService.findById(aprobadoPorId).orElse(null));
        return repository.save(liq);
    }

    @Transactional
    public LiquidacionSueldo volverBorrador(Long id) {
        LiquidacionSueldo liq = repository.findById(id).orElseThrow(() -> new GraphQLException("Liquidacion no encontrada"));
        if (liq.getEstado() != LiquidacionSueldoEstado.APROBADA) throw new GraphQLException("Solo se retrocede desde APROBADA");
        liq.setEstado(LiquidacionSueldoEstado.BORRADOR);
        liq.setFechaAprobacion(null);
        liq.setAprobadoPor(null);
        return repository.save(liq);
    }

    /**
     * Paga una liquidacion APROBADA: egreso real del neto en la Caja Mayor +
     * MovimientoPersonas(PAGO_SALARIO), y aplica los efectos cruzados de cada
     * item (vale DESCONTADO, cuota PAGADA, aguinaldo/venta PAGADO, bono ligado).
     */
    @Transactional
    public LiquidacionSueldo pagar(Long id, Long cajaVirtualId) {
        LiquidacionSueldo liq = repository.findById(id).orElseThrow(() -> new GraphQLException("Liquidacion no encontrada"));
        if (liq.getEstado() != LiquidacionSueldoEstado.APROBADA) throw new GraphQLException("Solo se paga una liquidacion APROBADA");

        CajaVirtual caja = cajaVirtualService.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja Mayor no encontrada"));
        MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
        mov.setCajaVirtual(caja);
        mov.setTipoMovimiento(CajaVirtualTipoMovimiento.EGRESO);
        mov.setCantidad(liq.getTotalNeto() != null ? liq.getTotalNeto().doubleValue() : 0.0);
        mov.setMoneda(liq.getMoneda());
        mov.setReferenciaId(liq.getId());
        mov.setDescripcion("PAGO SALARIO " + liq.getPeriodo() + " - LIQ #" + liq.getId());
        mov.setActivo(true);
        mov = movimientoCajaVirtualService.registrarMovimiento(mov);
        liq.setCajaVirtualId(cajaVirtualId);
        liq.setMovimientoCajaVirtualId(mov.getId());

        if (liq.getFuncionario() != null && liq.getFuncionario().getPersona() != null) {
            MovimientoPersonas mp = new MovimientoPersonas();
            mp.setPersona(liq.getFuncionario().getPersona());
            mp.setTipo(TipoMovimientoPersonas.PAGO_SALARIO);
            mp.setReferenciaId(liq.getId());
            mp.setValorTotal(liq.getTotalNeto() != null ? liq.getTotalNeto().doubleValue() : 0.0);
            mp.setActivo(true);
            mp.setObservacion("PAGO SALARIO " + liq.getPeriodo());
            mp = movimientoPersonasService.save(mp);
            liq.setMovimientoPersonaId(mp.getId());
        }

        aplicarEfectosCruzados(liq, true);

        liq.setEstado(LiquidacionSueldoEstado.PAGADA);
        liq.setFechaPago(LocalDateTime.now());
        return repository.save(liq);
    }

    /**
     * Anula una liquidacion PAGADA: contra-asiento AJUSTE en la caja y
     * reversion total de los efectos cruzados.
     */
    @Transactional
    public LiquidacionSueldo anular(Long id) {
        LiquidacionSueldo liq = repository.findById(id).orElseThrow(() -> new GraphQLException("Liquidacion no encontrada"));
        if (liq.getEstado() == LiquidacionSueldoEstado.ANULADA) return liq;
        if (liq.getEstado() == LiquidacionSueldoEstado.PAGADA && liq.getCajaVirtualId() != null) {
            CajaVirtual caja = cajaVirtualService.findById(liq.getCajaVirtualId())
                    .orElseThrow(() -> new GraphQLException("Caja Mayor no encontrada"));
            MovimientoCajaVirtual rev = new MovimientoCajaVirtual();
            rev.setCajaVirtual(caja);
            rev.setTipoMovimiento(CajaVirtualTipoMovimiento.AJUSTE);
            rev.setCantidad(liq.getTotalNeto() != null ? liq.getTotalNeto().doubleValue() : 0.0);
            rev.setMoneda(liq.getMoneda());
            rev.setReferenciaId(liq.getId());
            rev.setDescripcion("ANULACION LIQUIDACION #" + liq.getId());
            rev.setActivo(true);
            movimientoCajaVirtualService.registrarMovimiento(rev);
            aplicarEfectosCruzados(liq, false);
        }
        liq.setEstado(LiquidacionSueldoEstado.ANULADA);
        return repository.save(liq);
    }

    /** Aplica (pagar=true) o revierte (pagar=false) los efectos cruzados de los items. */
    private void aplicarEfectosCruzados(LiquidacionSueldo liq, boolean pagar) {
        for (LiquidacionItem it : itemRepository.findByLiquidacionIdOrderByIdAsc(liq.getId())) {
            String tipo = it.getReferenciaTipo();
            Long refId = it.getReferenciaId();
            if (tipo == null || refId == null) continue;
            switch (tipo) {
                case "VALE":
                case "ADELANTO":
                    valeRepository.findById(refId).ifPresent(v -> {
                        v.setEstado(pagar ? ValeEstado.DESCONTADO : ValeEstado.CONFIRMADO);
                        v.setLiquidacionId(pagar ? liq.getId() : null);
                        valeRepository.save(v);
                    });
                    break;
                case "CPP_CUOTA":
                    prestamoCuotaRepository.findById(refId).ifPresent(c -> {
                        BigDecimal monto = it.getMonto() != null ? it.getMonto() : BigDecimal.ZERO;
                        BigDecimal pagado = c.getMontoPagado() != null ? c.getMontoPagado() : BigDecimal.ZERO;
                        c.setMontoPagado(pagar ? pagado.add(monto) : pagado.subtract(monto).max(BigDecimal.ZERO));
                        c.setEstado(c.getMontoPagado().compareTo(c.getMonto() != null ? c.getMonto() : BigDecimal.ZERO) >= 0
                                ? PrestamoCuotaEstado.PAGADA : PrestamoCuotaEstado.PENDIENTE);
                        prestamoCuotaRepository.save(c);
                    });
                    break;
                case "AGUINALDO":
                    aguinaldoRepository.findById(refId).ifPresent(a -> {
                        a.setEstado(pagar ? AguinaldoEstado.PAGADO : AguinaldoEstado.APROBADO);
                        a.setLiquidacionId(pagar ? liq.getId() : null);
                        aguinaldoRepository.save(a);
                    });
                    break;
                case "VACACION_VENTA":
                    vacacionVentaRepository.findById(refId).ifPresent(vv -> {
                        vv.setEstado(pagar ? VacacionVentaEstado.PAGADO : VacacionVentaEstado.PENDIENTE);
                        vv.setLiquidacionId(pagar ? liq.getId() : null);
                        vacacionVentaRepository.save(vv);
                    });
                    break;
                case "BONO":
                    bonoRepository.findById(refId).ifPresent(b -> {
                        b.setLiquidacionId(pagar ? liq.getId() : null);
                        bonoRepository.save(b);
                    });
                    break;
                default:
                    break;
            }
        }
    }

    /** Genera borradores para todos los funcionarios activos en un periodo. */
    @Transactional
    public int generarMes(String periodo, Long monedaId) {
        int n = 0;
        for (Funcionario f : funcionarioService.findAll2()) {
            if (!Boolean.TRUE.equals(f.getActivo())) continue;
            try {
                generarBorrador(f.getId(), periodo, monedaId);
                n++;
            } catch (Exception ignored) {
            }
        }
        return n;
    }
}
