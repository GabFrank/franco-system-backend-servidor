package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Vuelto de un egreso de la caja mayor: el responsable devuelve lo que sobró de un egreso de efectivo,
 * egreso vario, egreso de maletín o pago de gasto.
 *
 * <ul>
 *   <li>Se registra como INGRESO en la misma caja, con origen VUELTO y {@code vueltoDeId} = el egreso.</li>
 *   <li>Puede devolverse en cualquier moneda: {@code vueltoEquivalente} lo expresa en la moneda del egreso
 *       con la última cotización, y es lo que descuenta del pendiente.</li>
 *   <li>Varios vueltos por egreso, hasta completarlo. Anular un vuelto devuelve el pendiente; el egreso no se
 *       puede anular mientras tenga vueltos activos (lo controla {@code TesoreriaService.revertir}).</li>
 *   <li>En un pago de un solo gasto, el vuelto se refleja en el gasto materializado
 *       ({@code financiero.gasto.vuelto_*}), que los reportes ya restan del gasto.</li>
 * </ul>
 */
@Service
@AllArgsConstructor
public class VueltoEgresoService {

    static final String OPERACION_VUELTO = "VUELTO_EGRESO_CAJA";
    /** Margen por redondeo de la conversión: una unidad de la moneda del egreso. */
    private static final BigDecimal TOLERANCIA = BigDecimal.ONE;
    private static final Long MONEDA_GUARANI = 1L;
    private static final Long MONEDA_REAL = 2L;
    private static final Long MONEDA_DOLAR = 3L;

    private final MovimientoCajaVirtualRepository movimientoRepository;
    private final MonedaRepository monedaRepository;
    private final CambioService cambioService;
    private final TesoreriaService tesoreriaService;
    private final TesoreriaSecurityService seguridad;
    private final IdempotenciaService idempotenciaService;
    private final PagoSolicitudDetalleRepository detalleRepository;
    private final SolicitudPagoRepository solicitudPagoRepository;
    private final GastoRepository gastoRepository;

    /** Una fila de la lista: el egreso, lo ya devuelto y lo que falta, los dos en la moneda del egreso. */
    @Data
    public static class EgresoParaVuelto {
        private MovimientoCajaVirtual movimiento;
        private Double devuelto;
        private Double pendiente;
    }

    @Transactional(readOnly = true)
    public Page<EgresoParaVuelto> egresosParaVuelto(Long cajaVirtualId, String texto, int page, int size) {
        if (cajaVirtualId == null) throw new GraphQLException("Debe indicar la caja.");
        seguridad.requireLecturaCaja(cajaVirtualId);
        String patron = texto == null || texto.trim().isEmpty() ? null : "%" + texto.trim().toUpperCase() + "%";
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<MovimientoCajaVirtualRepository.EgresoParaVueltoFila> filas =
                movimientoRepository.findEgresosParaVuelto(cajaVirtualId, patron, pageable);

        List<Long> ids = filas.getContent().stream().map(MovimientoCajaVirtualRepository.EgresoParaVueltoFila::getId)
                .collect(Collectors.toList());
        Map<Long, MovimientoCajaVirtual> porId = movimientoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MovimientoCajaVirtual::getId, m -> m));
        List<EgresoParaVuelto> contenido = new ArrayList<>();
        for (MovimientoCajaVirtualRepository.EgresoParaVueltoFila f : filas.getContent()) {
            MovimientoCajaVirtual m = porId.get(f.getId());
            if (m == null) continue;
            BigDecimal devuelto = f.getDevuelto() != null ? f.getDevuelto() : BigDecimal.ZERO;
            EgresoParaVuelto e = new EgresoParaVuelto();
            e.setMovimiento(m);
            e.setDevuelto(devuelto.doubleValue());
            e.setPendiente(pendiente(m, devuelto).doubleValue());
            contenido.add(e);
        }
        return new PageImpl<>(contenido, pageable, filas.getTotalElements());
    }

    /**
     * Registra el vuelto de un egreso. Todo o nada; con la misma {@code claveIdempotencia} no se registra dos veces.
     *
     * @param monedaId la moneda en que se devuelve (puede no ser la del egreso)
     * @param monto    lo que entra a la caja, en esa moneda
     */
    @Transactional
    public MovimientoCajaVirtual registrarVuelto(Long egresoId, Long monedaId, Double monto, String descripcion,
                                                 Usuario usuario, String claveIdempotencia) {
        if (usuario == null) throw new GraphQLException("No se pudo identificar al usuario.");
        if (egresoId == null) throw new GraphQLException("Debe indicar el egreso.");
        if (monedaId == null) throw new GraphQLException("Debe indicar la moneda del vuelto.");
        if (monto == null || !(monto > 0)) throw new GraphQLException("El monto del vuelto debe ser positivo.");
        BigDecimal cantidad = AltaIdempotenteService.monto(monto);
        String desc = descripcion == null || descripcion.trim().isEmpty() ? null : descripcion.trim().toUpperCase();
        String huella = new HuellaPedido().id(egresoId).id(monedaId).numero(cantidad).texto(desc).calcular();

        return idempotenciaService.ejecutar(claveIdempotencia, OPERACION_VUELTO, huella, usuario,
                () -> registrar(egresoId, monedaId, cantidad, desc, usuario),
                MovimientoCajaVirtual::getId,
                this::yaRegistrado);
    }

    private MovimientoCajaVirtual registrar(Long egresoId, Long monedaId, BigDecimal cantidad, String desc,
                                            Usuario usuario) {
        // El lock sobre el egreso serializa dos vueltos simultáneos del mismo egreso: el segundo ve el primero.
        MovimientoCajaVirtual egreso = movimientoRepository.lockById(egresoId)
                .orElseThrow(() -> new GraphQLException("Egreso no encontrado: " + egresoId));
        if (!movimientoRepository.findActivoById(egresoId).orElse(true)) {
            throw new GraphQLException("El egreso #" + egresoId + " está anulado.");
        }
        if (!admiteVuelto(egreso)) {
            throw new GraphQLException("El movimiento #" + egresoId + " no es un egreso que se pueda rendir con vuelto.");
        }
        Long cajaId = egreso.getCajaVirtual() != null ? egreso.getCajaVirtual().getId() : null;
        seguridad.requireEscrituraCaja(cajaId);

        Moneda moneda = monedaRepository.findById(monedaId)
                .orElseThrow(() -> new GraphQLException("Moneda no encontrada: " + monedaId));
        Moneda monedaEgreso = egreso.getMoneda();
        BigDecimal equivalente = convertir(cantidad, moneda, monedaEgreso);

        BigDecimal pendiente = pendiente(egreso, movimientoRepository.sumVueltoActivo(egresoId));
        if (equivalente.subtract(pendiente).compareTo(TOLERANCIA) > 0) {
            throw new GraphQLException("El vuelto (" + equivalente.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                    + " " + simbolo(monedaEgreso) + ") supera lo pendiente del egreso ("
                    + pendiente.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " " + simbolo(monedaEgreso) + ").");
        }
        // Dentro del margen de redondeo, completa el egreso justo.
        if (equivalente.compareTo(pendiente) > 0) equivalente = pendiente;

        MovimientoCajaVirtual v = new MovimientoCajaVirtual();
        v.setCajaVirtual(egreso.getCajaVirtual());
        v.setTipoMovimiento(CajaVirtualTipoMovimiento.INGRESO);
        v.setCantidad(cantidad.doubleValue());
        v.setMoneda(moneda);
        v.setUsuario(usuario);
        v.setResponsable(egreso.getResponsable());
        v.setDescripcion(descripcionDe(egreso, desc));
        v.setReferenciaId(egresoId);
        v.setOrigenTipo(OrigenMovimientoTipo.VUELTO);
        v.setOrigenId(egresoId);
        v.setVueltoDeId(egresoId);
        v.setVueltoEquivalente(equivalente);
        v.setActivo(true);
        MovimientoCajaVirtual posteado = tesoreriaService.registrar(v);

        actualizarGasto(egreso);
        return posteado;
    }

    /** Repetición de un pedido ya registrado: devuelve el vuelto, salvo que se haya anulado después. */
    private MovimientoCajaVirtual yaRegistrado(Long movimientoId) {
        MovimientoCajaVirtual m = movimientoRepository.findById(movimientoId).orElse(null);
        if (m == null || Boolean.FALSE.equals(m.getActivo())) {
            throw new GraphQLException("Ese vuelto ya se había registrado y después se anuló.");
        }
        return m;
    }

    @EventListener
    public void alRevertirVuelto(VueltoRevertidoEvent evento) {
        movimientoRepository.findById(evento.getEgresoId()).ifPresent(this::actualizarGasto);
    }

    // ─────────────── Reglas ───────────────

    /** Los cuatro egresos que se rinden con vuelto: efectivo, vario, maletín y pago de gasto. */
    static boolean admiteVuelto(MovimientoCajaVirtual m) {
        OrigenMovimientoTipo o = m.getOrigenTipo();
        if (m.getTipoMovimiento() == CajaVirtualTipoMovimiento.EGRESO) {
            return o == null || o == OrigenMovimientoTipo.MANUAL || o == OrigenMovimientoTipo.ENTRADA_VARIA
                    || o == OrigenMovimientoTipo.MALETIN;
        }
        return m.getTipoMovimiento() == CajaVirtualTipoMovimiento.PAGO_PROVEEDOR && o == OrigenMovimientoTipo.GASTO;
    }

    private static BigDecimal pendiente(MovimientoCajaVirtual egreso, BigDecimal devuelto) {
        BigDecimal total = egreso.getCantidad() != null ? BigDecimal.valueOf(egreso.getCantidad()).abs() : BigDecimal.ZERO;
        BigDecimal p = total.subtract(devuelto != null ? devuelto : BigDecimal.ZERO);
        return p.signum() > 0 ? p : BigDecimal.ZERO;
    }

    private static String descripcionDe(MovimientoCajaVirtual egreso, String desc) {
        String base = "VUELTO DE #" + egreso.getId()
                + (egreso.getDescripcion() != null && !egreso.getDescripcion().isEmpty() ? " - " + egreso.getDescripcion() : "");
        String completa = desc != null ? base + " - " + desc : base;
        return completa.length() > 250 ? completa.substring(0, 250) : completa;
    }

    // ─────────────── Conversión ───────────────

    /** {@code monto} de {@code desde} expresado en {@code hacia}, con la última cotización de cada una. */
    BigDecimal convertir(BigDecimal monto, Moneda desde, Moneda hacia) {
        if (desde == null || hacia == null || Objects.equals(desde.getId(), hacia.getId())) return monto;
        BigDecimal enGs = monto.multiply(valorEnGs(desde));
        return enGs.divide(valorEnGs(hacia), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal valorEnGs(Moneda moneda) {
        if (MONEDA_GUARANI.equals(moneda.getId())
                || (moneda.getDenominacion() != null && moneda.getDenominacion().toUpperCase().contains("GUARAN"))) {
            return BigDecimal.ONE;
        }
        Double valor = cambioService.findLastValorEnGsByMonedaId(moneda.getId());
        if (valor == null || !(valor > 0)) {
            throw new GraphQLException("No hay cotización cargada para " + moneda.getDenominacion()
                    + ": no se puede convertir el vuelto.");
        }
        return BigDecimal.valueOf(valor);
    }

    private static String simbolo(Moneda m) {
        if (m == null) return "";
        return m.getSimbolo() != null ? m.getSimbolo() : (m.getDenominacion() != null ? m.getDenominacion() : "");
    }

    // ─────────────── Gasto materializado ───────────────

    /**
     * Refleja en el gasto lo devuelto de sus pagos. Solo cuando el pago es de UN gasto: con varios, el vuelto no
     * se puede atribuir a uno. Suma los vueltos activos de todos los pagos de ese gasto (pudo pagarse en partes),
     * convertidos a la moneda del gasto, y lo deja en su columna de vuelto, que los reportes restan.
     */
    void actualizarGasto(MovimientoCajaVirtual egreso) {
        if (egreso.getOrigenTipo() != OrigenMovimientoTipo.GASTO || egreso.getOrigenId() == null) return;
        Set<Long> solicitudes = detallesActivos(detalleRepository.findByPagoIdOrderByCreadoEnAsc(egreso.getOrigenId()))
                .map(PagoSolicitudDetalle::getSolicitudPagoId).collect(Collectors.toSet());
        if (solicitudes.size() != 1) return;
        Long solicitudId = solicitudes.iterator().next();
        SolicitudPago sp = solicitudPagoRepository.findById(solicitudId).orElse(null);
        Gasto gasto = gastoRepository.findFirstBySolicitudPagoId(solicitudId);
        if (sp == null || gasto == null) return;

        // Los movimientos de caja de los pagos de un solo gasto (este) por los que se pagó la solicitud.
        Set<Long> egresos = new LinkedHashSet<>();
        for (PagoSolicitudDetalle d : detallesActivos(detalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(solicitudId))
                .collect(Collectors.toList())) {
            if (d.getMovimientoCajaVirtualId() == null) continue;
            boolean pagoDeUnSoloGasto = detallesActivos(detalleRepository.findByPagoIdOrderByCreadoEnAsc(d.getPagoId()))
                    .allMatch(o -> solicitudId.equals(o.getSolicitudPagoId()));
            if (pagoDeUnSoloGasto) egresos.add(d.getMovimientoCajaVirtualId());
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : egresos) {
            MovimientoCajaVirtual e = movimientoRepository.findById(id).orElse(null);
            if (e == null) continue;
            BigDecimal devuelto = movimientoRepository.sumVueltoActivo(id);
            if (devuelto == null || devuelto.signum() == 0) continue;
            total = total.add(convertir(devuelto, e.getMoneda(), sp.getMoneda()));
        }
        Long monedaId = sp.getMoneda() != null ? sp.getMoneda().getId() : MONEDA_GUARANI;
        double vuelto = total.setScale(2, RoundingMode.HALF_UP).doubleValue();
        gasto.setVueltoGs(MONEDA_GUARANI.equals(monedaId) ? vuelto : 0.0);
        gasto.setVueltoRs(MONEDA_REAL.equals(monedaId) ? vuelto : 0.0);
        gasto.setVueltoDs(MONEDA_DOLAR.equals(monedaId) ? vuelto : 0.0);
        gastoRepository.save(gasto);
    }

    private static java.util.stream.Stream<PagoSolicitudDetalle> detallesActivos(List<PagoSolicitudDetalle> detalles) {
        return detalles == null ? java.util.stream.Stream.empty()
                : detalles.stream().filter(d -> !Boolean.TRUE.equals(d.getAnulado()));
    }
}
