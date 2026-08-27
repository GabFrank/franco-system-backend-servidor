package com.franco.dev.service.rrhh;

import com.franco.dev.service.rrhh.builder.AguinaldoCalculator;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Aguinaldo;
import com.franco.dev.domain.rrhh.enums.AguinaldoEstado;
import com.franco.dev.repository.rrhh.AguinaldoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.personas.FuncionarioService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class AguinaldoService extends CrudService<Aguinaldo, AguinaldoRepository, Long> {

    private final AguinaldoRepository repository;
    private final com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository pagoSolicitudDetalleRepository;
    private final ConfiguracionRrhhService configuracionRrhhService;
    private final FuncionarioService funcionarioService;
    private final CajaVirtualService cajaVirtualService;
    private final MovimientoCajaVirtualService movimientoCajaVirtualService;
    private final MonedaService monedaService;
    private final BaseRemunerativaService baseRemunerativaService;

    /** Valores de {@code Aguinaldo.origenBase}. */
    public static final String ORIGEN_PERCIBIDO = "PERCIBIDO";
    public static final String ORIGEN_SUELDO_ACTUAL = "SUELDO_ACTUAL";

    @Override
    public AguinaldoRepository getRepository() {
        return repository;
    }

    public List<Aguinaldo> findByAnio(Integer anio) {
        return repository.findByAnioOrderByIdAsc(anio);
    }

    public List<Aguinaldo> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByAnioDesc(funcionarioId);
    }

    public Page<Aguinaldo> findPage(Integer anio, Long funcionarioId, Pageable pageable) {
        return repository.findPage(anio, funcionarioId, pageable);
    }

    /**
     * Calcula el aguinaldo del anio para todos los funcionarios activos.
     *
     * <p><b>aguinaldo = lo percibido en el anio / 12.</b> Percibido es la suma de los
     * items HABER remunerativos de las liquidaciones APROBADA o PAGADA -- no el sueldo
     * actual multiplicado por meses, que era lo que se hacia antes y no miraba ni el
     * historial salarial ni las horas extra, bonos o comisiones.</p>
     *
     * <p>La formula no es nueva en el sistema: es la que
     * {@code LiquidacionFinalService.calcularAguinaldoProporcional()} ya usaba para el
     * finiquito. Lo que se corrige aca es que convivieran dos, y que el mismo funcionario
     * cobrara distinto segun si se quedaba o si se iba.</p>
     *
     * <p>Idempotente por (funcionario, anio): si ya existe CALCULADO lo actualiza y guarda
     * el monto anterior; no toca los APROBADO/PAGADO. Devuelve la cantidad procesada.</p>
     */
    @Transactional
    public int calcularAguinaldosAnio(Integer anio) {
        int n = 0;
        for (Funcionario f : funcionarioService.findAll2()) {
            if (!Boolean.TRUE.equals(f.getActivo())) continue;
            // El sueldo ya no es requisito: quien tenga liquidaciones cargadas se calcula
            // desde lo percibido. Solo hace falta para el fallback, y ahi se valida.
            if (f.getFechaIngreso() == null) continue;

            if (f.getFechaIngreso().getYear() > anio) {
                continue;
            }
            LocalDate ingreso = f.getFechaIngreso().toLocalDate();
            // Devengado = lo ganado hasta hoy. Proyectado = lo que se va a deber al 31/12.
            // En un anio ya terminado coinciden.
            int mesesDevengados = AguinaldoCalculator.mesesDevengados(anio, ingreso, LocalDate.now());
            int mesesProyectados = AguinaldoCalculator.mesesTrabajados(anio, ingreso);

            BigDecimal sueldo = f.getSueldo();
            BigDecimal monto = AguinaldoCalculator.calcularMonto(sueldo, mesesDevengados);
            BigDecimal montoProyectado = AguinaldoCalculator.calcularMonto(sueldo, mesesProyectados);

            Optional<Aguinaldo> existente = repository.findByFuncionarioIdAndAnio(f.getId(), anio);
            Aguinaldo a = existente.orElseGet(Aguinaldo::new);
            if (a.getEstado() == AguinaldoEstado.APROBADO || a.getEstado() == AguinaldoEstado.PAGADO) {
                continue;
            }
            a.setFuncionario(f);
            a.setAnio(anio);
            a.setMesesTrabajados(mesesDevengados);
            a.setMontoCalculado(monto);
            a.setMesesProyectados(mesesProyectados);
            a.setMontoProyectado(montoProyectado);
            a.setEstado(AguinaldoEstado.CALCULADO);
            if (a.getCreadoEn() == null) a.setCreadoEn(LocalDateTime.now());
            repository.save(a);
            n++;
        }
        return n;
    }

    @Transactional
    public Aguinaldo aprobar(Long id) {
        Aguinaldo a = repository.findById(id)
                .orElseThrow(() -> new GraphQLException("Aguinaldo no encontrado"));
        // Aprobar congela el monto: el recalculo no vuelve a tocar un APROBADO. Hacerlo
        // antes de que el anio termine dejaria fijado un devengado parcial, y la
        // liquidacion de diciembre pagaria de menos.
        int mesAguinaldo = configuracionRrhhService.getNumber("MES_AGUINALDO", new BigDecimal("12")).intValue();
        LocalDate hoy = LocalDate.now();
        if (a.getAnio() != null && hoy.getYear() == a.getAnio() && hoy.getMonthValue() < mesAguinaldo) {
            throw new GraphQLException("Todavia no se puede aprobar el aguinaldo " + a.getAnio()
                    + ": recien esta devengado " + a.getMesesTrabajados() + "/12. Se aprueba a partir del mes "
                    + mesAguinaldo + ".");
        }
        a.setEstado(AguinaldoEstado.APROBADO);
        return repository.save(a);
    }

    /**
     * Paga el aguinaldo por separado (fuera de la liquidación mensual): egreso de
     * Caja Mayor + estado PAGADO. Al quedar PAGADO deja de sumarse en la liquidación
     * de diciembre (que solo incluye aguinaldos APROBADO). Patrón: PrestamoService.desembolsar.
     */
    @Transactional
    public Aguinaldo pagar(Long id, Long cajaVirtualId) {
        Aguinaldo a = repository.findById(id)
                .orElseThrow(() -> new GraphQLException("Aguinaldo no encontrado"));
        if (a.getEstado() != AguinaldoEstado.APROBADO) {
            throw new GraphQLException("Solo se paga un aguinaldo APROBADO (estado actual: " + a.getEstado() + ")");
        }
        if (cajaVirtualId == null) throw new GraphQLException("Debe seleccionar la Caja Mayor");
        CajaVirtual caja = cajaVirtualService.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja Mayor no encontrada"));
        BigDecimal monto = a.getMontoCalculado() != null ? a.getMontoCalculado() : BigDecimal.ZERO;
        Moneda moneda = a.getFuncionario() != null && a.getFuncionario().getMoneda() != null
                ? a.getFuncionario().getMoneda() : monedaService.findById(1L).orElse(null);

        MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
        mov.setCajaVirtual(caja);
        mov.setTipoMovimiento(CajaVirtualTipoMovimiento.EGRESO);
        mov.setCantidad(monto.doubleValue());
        mov.setMoneda(moneda);
        mov.setReferenciaId(a.getId());
        mov.setOrigenTipo(OrigenMovimientoTipo.RRHH_AGUINALDO);
        mov.setOrigenId(a.getId());
        mov.setDescripcion("PAGO AGUINALDO " + (a.getAnio() != null ? a.getAnio() : "") + " #" + a.getId()
                + (a.getFuncionario() != null && a.getFuncionario().getPersona() != null
                        && a.getFuncionario().getPersona().getNombre() != null
                        ? " - " + a.getFuncionario().getPersona().getNombre() : ""));
        mov.setUsuario(a.getUsuario());
        mov.setActivo(true);
        mov = movimientoCajaVirtualService.registrarMovimiento(mov);

        a.setCajaVirtualId(cajaVirtualId);
        a.setMovimientoCajaVirtualId(mov.getId());
        a.setEstado(AguinaldoEstado.PAGADO);
        a.setFechaPago(LocalDate.now());
        return repository.save(a);
    }


    /**
     * true si esta obligacion de pago pertenece a un aguinaldo. Lo usa el motor de pago para
     * saber que concepto esta pagando y etiquetar el movimiento de caja con su origen real.
     */
    @Transactional(readOnly = true)
    public boolean tieneSolicitud(Long solicitudPagoId) {
        return solicitudPagoId != null && repository.findBySolicitudPagoId(solicitudPagoId) != null;
    }

    /**
     * Espejo de {@code ValeService.sincronizarDesdeSolicitudPago}: lo llama el motor de pago
     * cuando el aguinaldo se paga desde el hub de la caja en vez de con {@link #pagar}.
     *
     * <p>Solicitud CONCLUIDA ⇒ aguinaldo PAGADO (y por lo tanto deja de sumarse en la
     * liquidacion de diciembre, que solo incluye los APROBADO) + link de caja y movimiento.
     * <b>No postea nada en caja</b>: el movimiento ya lo genero el motor de pago. Si el pago
     * se anula, vuelve a APROBADO y por ende vuelve a entrar en la liquidacion.</p>
     */
    @Transactional
    public void sincronizarDesdeSolicitudPago(com.franco.dev.domain.operaciones.SolicitudPago sp) {
        if (sp == null || sp.getTipo() != com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.RRHH) return;
        Aguinaldo a = repository.findBySolicitudPagoId(sp.getId());
        if (a == null) return;

        boolean pagado = sp.getEstado() == com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado.CONCLUIDO;
        AguinaldoEstado destino = pagado ? AguinaldoEstado.PAGADO : AguinaldoEstado.APROBADO;
        if (a.getEstado() == destino) return;   // idempotente: el motor puede reentrar

        if (pagado) {
            for (com.franco.dev.domain.financiero.PagoSolicitudDetalle d
                    : pagoSolicitudDetalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(sp.getId())) {
                if (Boolean.TRUE.equals(d.getAnulado())) continue;
                if (d.getMovimientoCajaVirtualId() != null) {
                    a.setMovimientoCajaVirtualId(d.getMovimientoCajaVirtualId());
                    a.setCajaVirtualId(d.getCajaVirtualId());
                    break;
                }
            }
            a.setFechaPago(LocalDate.now());
        } else if (sp.getMontoPagado() == null || sp.getMontoPagado().signum() <= 0) {
            // Solo se sueltan los links cuando NO queda plata aplicada. Si la obligacion quedo
            // PARCIAL (se pago en dos eventos y se anulo uno solo), parte del dinero sigue
            // fuera de la caja: borrar la referencia al movimiento perderia su rastro.
            a.setMovimientoCajaVirtualId(null);
            a.setCajaVirtualId(null);
            a.setFechaPago(null);
        }
        a.setEstado(destino);
        repository.save(a);
    }

    @Override
    public Aguinaldo save(Aguinaldo entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getEstado() == null) entity.setEstado(AguinaldoEstado.CALCULADO);
        return super.save(entity);
    }
}
