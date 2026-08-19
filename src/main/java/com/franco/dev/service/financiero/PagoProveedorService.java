package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.domain.financiero.enums.*;
import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.PagoEstado;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.service.operaciones.PagoService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CPP — pago de solicitudes de pago (proveedor). SolicitudPago actúa como CPP (central-only) y es
 * la <b>única fuente de verdad</b> de la deuda (montoPagado/estado; sin ledger de proveedor).
 * <p>
 * Un pago es un <b>evento</b> ({@link Pago}) que agrupa varias notas y varias líneas (mixto:
 * caja mayor / cuenta bancaria / ajuste Fx). Los movimientos físicos se <b>consolidan</b> por
 * (fuente, caja/cuenta, moneda): un único MovimientoCajaVirtual/MovimientoBancario por grupo,
 * descripción "Pago a {proveedor}". Cada {@link PagoSolicitudDetalle} referencia el movimiento
 * consolidado y el evento (pagoId), lo que permite anular todo el evento de una sola vez.
 */
@Service
@AllArgsConstructor
public class PagoProveedorService {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.005");

    private final SolicitudPagoService solicitudPagoService;
    private final PagoService pagoService;
    private final TesoreriaService tesoreriaService;
    private final BancoLedgerService bancoLedgerService;
    private final ChequeGestionService chequeGestionService;
    private final com.franco.dev.repository.financiero.ChequeraRepository chequeraRepository;
    private final CajaVirtualRepository cajaVirtualRepository;
    private final MonedaRepository monedaRepository;
    private final com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository detalleRepository;
    private final com.franco.dev.repository.financiero.MovimientoBancarioRepository movimientoBancarioRepository;
    private final PreGastoService preGastoService;
    // Sin ciclo: ValeService no conoce el motor de pago (el que sí lo usa es ValeTesoreriaService).
    private final com.franco.dev.service.rrhh.ValeService valeService;

    /** Una línea de pago (pago mixto). Puede ser fuente AJUSTE (diferencia de cambio). */
    @Data
    public static class LineaPago {
        private FuentePago fuente;
        private Long cajaVirtualId;
        private Long cuentaBancariaId;
        private Long monedaId;
        private BigDecimal monto;          // monto de la línea en su moneda
        private BigDecimal cotizacion;     // hacia la moneda de la solicitud
        private BigDecimal montoSolicitud; // monto convertido a la moneda de la solicitud (magnitud)
        private Boolean descuento;         // ajuste Fx a favor (pagó de menos): suma a la deuda saldada
        private Boolean aumento;           // ajuste Fx en contra (pagó de más): resta de la deuda saldada
        // --- Fuente CHEQUE: 1 cheque físico por chequeRef (aunque el FIFO lo reparta entre notas) ---
        private Long chequeRef;                      // agrupa las partes de un mismo cheque
        private Long chequeraId;
        private Boolean diferido;
        private java.time.LocalDateTime fechaEmision; // fecha de emisión (fechaEntrega del cheque)
        private java.time.LocalDateTime fechaPago;    // vencimiento del cheque
        private String beneficiario;                  // default = nombre del proveedor
        private Boolean nominal;                       // true nominal, false al portador
    }

    /** Una línea de pago en lote desde la caja mayor (misma moneda, efectivo). */
    @Data
    public static class PagoLote {
        private Long solicitudId;
        private Long monedaId;
        private BigDecimal monto;
    }

    /** Una solicitud con su subset de líneas de pago (pago mixto multi-fuente). */
    @Data
    public static class SolicitudConLineas {
        private Long solicitudId;
        private List<LineaPago> lineas;
    }

    /** Aporte firmado a la deuda saldada: aumento resta, todo lo demás suma. */
    private static BigDecimal aporte(BigDecimal montoSolicitud, Boolean aumento) {
        return Boolean.TRUE.equals(aumento) ? montoSolicitud.negate() : montoSolicitud;
    }

    /** Grupo de consolidación de un movimiento físico. */
    private static class GrupoMov {
        FuentePago fuente;
        Long cajaVirtualId;
        Long cuentaBancariaId;
        Long monedaId;
        BigDecimal suma = BigDecimal.ZERO;
        Long movimientoId; // id del movimiento consolidado una vez posteado
    }

    private static String claveGrupo(LineaPago l) {
        Long ref = l.getFuente() == FuentePago.CAJA_MAYOR ? l.getCajaVirtualId() : l.getCuentaBancariaId();
        return l.getFuente() + "|" + ref + "|" + l.getMonedaId();
    }

    // ── Entradas públicas ──

    /** Pago mixto de varias solicitudes, todo como un único evento consolidado. Atómico. */
    @Transactional
    public Pago pagarLoteMixto(List<SolicitudConLineas> pagos, Usuario usuario) {
        return procesarEvento(pagos, usuario);
    }

    /** Pago de una sola solicitud (un evento con una nota). */
    @Transactional
    public SolicitudPago pagar(Long solicitudId, List<LineaPago> lineas, Usuario usuario) {
        SolicitudConLineas s = new SolicitudConLineas();
        s.setSolicitudId(solicitudId);
        s.setLineas(lineas);
        procesarEvento(List.of(s), usuario);
        return solicitudPagoService.getRepository().findById(solicitudId).orElse(null);
    }

    /** Pago de varias solicitudes desde la caja mayor (efectivo), como un único evento consolidado. */
    @Transactional
    public Pago pagarLoteCajaMayor(Long cajaVirtualId, List<PagoLote> pagos, Usuario usuario) {
        if (cajaVirtualId == null) throw new GraphQLException("Caja mayor requerida");
        if (pagos == null || pagos.isEmpty()) throw new GraphQLException("Seleccione al menos una solicitud a pagar");
        List<SolicitudConLineas> ls = new ArrayList<>();
        for (PagoLote p : pagos) {
            if (p.getMonto() == null || p.getMonto().signum() <= 0) throw new GraphQLException("Monto a pagar inválido");
            LineaPago l = new LineaPago();
            l.setFuente(FuentePago.CAJA_MAYOR);
            l.setCajaVirtualId(cajaVirtualId);
            l.setMonedaId(p.getMonedaId());
            l.setMonto(p.getMonto());
            l.setMontoSolicitud(p.getMonto());
            SolicitudConLineas s = new SolicitudConLineas();
            s.setSolicitudId(p.getSolicitudId());
            s.setLineas(List.of(l));
            ls.add(s);
        }
        return procesarEvento(ls, usuario);
    }

    /** Solicitudes de pago de COMPRA pagables (SOLICITADO o PARCIAL) para el diálogo de compras.
     *  PENDIENTE es borrador y NO es pagable. Excluye GASTO (tienen su propio diálogo). Filtra por
     *  proveedor si se indica. */
    public List<SolicitudPago> listarPendientes(Long proveedorId) {
        List<SolicitudPago> list = solicitudPagoService.getRepository()
                .findByEstadoIn(List.of(SolicitudPagoEstado.SOLICITADO, SolicitudPagoEstado.PARCIAL))
                .stream()
                .filter(s -> s.getTipo() != com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.GASTO)
                .collect(Collectors.toList());
        if (proveedorId != null) {
            list = list.stream()
                    .filter(s -> s.getProveedor() != null && proveedorId.equals(s.getProveedor().getId()))
                    .collect(Collectors.toList());
        }
        return list;
    }

    /** Solicitudes de pago de GASTO pagables (SOLICITADO o PARCIAL) para el diálogo de gastos. */
    public List<SolicitudPago> listarGastosPendientes() {
        return solicitudPagoService.getRepository()
                .findByEstadoIn(List.of(SolicitudPagoEstado.SOLICITADO, SolicitudPagoEstado.PARCIAL))
                .stream()
                .filter(s -> s.getTipo() == com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.GASTO)
                .collect(Collectors.toList());
    }

    // ── Núcleo: procesar un evento de pago consolidado ──

    private Pago procesarEvento(List<SolicitudConLineas> pagos, Usuario usuario) {
        if (pagos == null || pagos.isEmpty()) throw new GraphQLException("Seleccione al menos una solicitud a pagar");

        // 1) Lock + validación + tope por solicitud (serializa pagos parciales concurrentes).
        LinkedHashMap<Long, SolicitudPago> spById = new LinkedHashMap<>();
        Map<Long, BigDecimal> aplicadoById = new HashMap<>();
        Proveedor proveedor = null;
        for (SolicitudConLineas p : pagos) {
            SolicitudPago sp = solicitudPagoService.getRepository().lockById(p.getSolicitudId()).orElse(null);
            if (sp == null) throw new GraphQLException("Solicitud de pago no encontrada: " + p.getSolicitudId());
            if (sp.getEstado() == SolicitudPagoEstado.CONCLUIDO || sp.getEstado() == SolicitudPagoEstado.CANCELADO) {
                throw new GraphQLException("La solicitud #" + sp.getId() + " ya está " + sp.getEstado());
            }
            if (spById.containsKey(sp.getId())) throw new GraphQLException("La solicitud #" + sp.getId() + " está repetida en el pago");
            if (proveedor != null && sp.getProveedor() != null && proveedor.getId() != null
                    && !proveedor.getId().equals(sp.getProveedor().getId())) {
                throw new GraphQLException("Todas las notas del pago deben ser del mismo proveedor");
            }
            if (p.getLineas() == null || p.getLineas().isEmpty()) throw new GraphQLException("Debe indicar al menos una línea de pago");

            BigDecimal total = BigDecimal.valueOf(sp.getMontoTotal() != null ? sp.getMontoTotal() : 0.0);
            BigDecimal pagado = sp.getMontoPagado() != null ? sp.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal restante = total.subtract(pagado);
            BigDecimal aplicado = BigDecimal.ZERO;
            for (LineaPago l : p.getLineas()) {
                BigDecimal montoSol = l.getMontoSolicitud() != null ? l.getMontoSolicitud() : l.getMonto();
                if (montoSol == null || montoSol.signum() <= 0) throw new GraphQLException("Monto de línea inválido");
                // El monto aplicado a la deuda debe corresponder al físico movido: montoSolicitud ≈ monto × cotización.
                if (l.getFuente() != FuentePago.AJUSTE) {
                    BigDecimal cotiz = l.getCotizacion() != null ? l.getCotizacion() : BigDecimal.ONE;
                    BigDecimal monto = l.getMonto() != null ? l.getMonto() : BigDecimal.ZERO;
                    BigDecimal esperado = monto.multiply(cotiz);
                    BigDecimal permitido = cotiz.abs().max(BigDecimal.ONE); // ~1 unidad de la línea, por redondeo cross-rate
                    if (montoSol.subtract(esperado).abs().compareTo(permitido) > 0) {
                        throw new GraphQLException("Línea de pago inconsistente: el monto aplicado no corresponde al monto × cotización");
                    }
                }
                aplicado = aplicado.add(aporte(montoSol, l.getAumento()));
            }
            if (aplicado.subtract(restante).compareTo(TOLERANCIA) > 0) {
                throw new GraphQLException("El pago excede el saldo de la solicitud #" + sp.getId() + " (" + restante + ")");
            }
            spById.put(sp.getId(), sp);
            aplicadoById.put(sp.getId(), aplicado);
            if (proveedor == null && sp.getProveedor() != null) proveedor = sp.getProveedor();
        }

        // 2) Cabecera del evento de pago.
        Pago pago = new Pago();
        pago.setUsuario(usuario);
        pago.setCreadoEn(LocalDateTime.now());
        pago.setEstado(PagoEstado.CONCLUIDO);
        pago.setProgramado(false);
        pago = pagoService.save(pago);

        // 3) Consolidar líneas físicas por (fuente, caja/cuenta, moneda) y postear un movimiento por grupo.
        boolean tieneProveedor = proveedor != null && proveedor.getPersona() != null && proveedor.getPersona().getNombre() != null;
        String provNombre = tieneProveedor ? proveedor.getPersona().getNombre() : "proveedor";
        // Un gasto puede no tener beneficiario proveedor → etiqueta genérica en el movimiento/cheque.
        String etiquetaPago = tieneProveedor ? ("Pago a " + provNombre) : "Pago de gasto";
        // Gasto: descripción rica en el movimiento → "#id - categoría - proveedor - descripción".
        SolicitudPago gastoSol = spById.values().stream()
                .filter(s -> s.getTipo() == com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.GASTO)
                .findFirst().orElse(null);
        if (gastoSol != null) {
            String cat = gastoSol.getTipoGasto() != null ? gastoSol.getTipoGasto().getDescripcion() : "Sin categoría";
            String benef = tieneProveedor ? provNombre : "—";
            String desc = gastoSol.getObservaciones() != null ? gastoSol.getObservaciones() : "";
            etiquetaPago = "#" + gastoSol.getId() + " - " + cat + " - " + benef + " - " + desc;
        }
        // Vales de RRHH: la descripción del vale ya trae "VALE #id - FUNCIONARIO - MOTIVO".
        // Con varios vales en el mismo evento, una sola de esas etiquetas sería engañosa.
        List<SolicitudPago> valeSols = spById.values().stream()
                .filter(s -> s.getTipo() == com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.RRHH)
                .collect(Collectors.toList());
        if (valeSols.size() == 1) {
            String desc = valeSols.get(0).getObservaciones();
            if (desc != null && !desc.trim().isEmpty()) etiquetaPago = desc;
        } else if (valeSols.size() > 1) {
            etiquetaPago = "Pago de vales (" + valeSols.size() + ")";
        }
        LinkedHashMap<String, GrupoMov> grupos = new LinkedHashMap<>();
        for (SolicitudConLineas p : pagos) {
            for (LineaPago l : p.getLineas()) {
                // AJUSTE no mueve efectivo; CHEQUE emite 1 cheque por línea (no se consolida).
                if (l.getFuente() == FuentePago.AJUSTE || l.getFuente() == FuentePago.CHEQUE) continue;
                GrupoMov g = grupos.computeIfAbsent(claveGrupo(l), k -> {
                    GrupoMov ng = new GrupoMov();
                    ng.fuente = l.getFuente();
                    ng.cajaVirtualId = l.getCajaVirtualId();
                    ng.cuentaBancariaId = l.getCuentaBancariaId();
                    ng.monedaId = l.getMonedaId();
                    return ng;
                });
                g.suma = g.suma.add(l.getMonto() != null ? l.getMonto() : BigDecimal.ZERO);
            }
        }
        for (GrupoMov g : grupos.values()) {
            if (g.suma.signum() <= 0) throw new GraphQLException("Monto de línea inválido");
            if (g.fuente == FuentePago.CAJA_MAYOR) {
                Moneda moneda = g.monedaId != null ? monedaRepository.findById(g.monedaId).orElse(null) : null;
                CajaVirtual caja = cajaVirtualRepository.findById(g.cajaVirtualId)
                        .orElseThrow(() -> new GraphQLException("Caja mayor no encontrada"));
                MovimientoCajaVirtual m = new MovimientoCajaVirtual();
                m.setCajaVirtual(caja);
                m.setTipoMovimiento(CajaVirtualTipoMovimiento.PAGO_PROVEEDOR);
                m.setCantidad(g.suma.doubleValue());
                m.setMoneda(moneda);
                m.setUsuario(usuario);
                m.setDescripcion(etiquetaPago);
                m.setReferenciaId(pago.getId());
                m.setOrigenTipo(OrigenMovimientoTipo.PAGO_CPP);
                m.setOrigenId(pago.getId());
                g.movimientoId = tesoreriaService.registrar(m).getId();
            } else if (g.fuente == FuentePago.CUENTA_BANCARIA) {
                MovimientoBancario mb = bancoLedgerService.registrar(g.cuentaBancariaId, MovimientoBancarioTipo.SALIDA_MANUAL,
                        g.suma, etiquetaPago, OrigenMovimientoTipo.PAGO_CPP.name(), pago.getId(), usuario);
                g.movimientoId = mb != null ? mb.getId() : null;
            } else {
                throw new GraphQLException("Fuente de pago no soportada para movimiento consolidado");
            }
        }

        // 3b) Emitir cheques: 1 cheque por chequeRef (total = Σ de sus partes), aunque el FIFO lo reparta entre notas.
        java.util.IdentityHashMap<LineaPago, Long> refDeLinea = new java.util.IdentityHashMap<>();
        Map<Long, BigDecimal> sumaChequeRef = new LinkedHashMap<>();
        Map<Long, LineaPago> primeraLineaCheque = new LinkedHashMap<>();
        long synthRef = -1;
        for (SolicitudConLineas p : pagos) {
            for (LineaPago l : p.getLineas()) {
                if (l.getFuente() != FuentePago.CHEQUE) continue;
                Long ref = l.getChequeRef() != null ? l.getChequeRef() : synthRef--;
                refDeLinea.put(l, ref);
                sumaChequeRef.merge(ref, l.getMonto() != null ? l.getMonto() : BigDecimal.ZERO, BigDecimal::add);
                primeraLineaCheque.putIfAbsent(ref, l);
            }
        }
        Map<Long, Cheque> chequePorRef = new LinkedHashMap<>();
        for (Map.Entry<Long, LineaPago> e : primeraLineaCheque.entrySet()) {
            chequePorRef.put(e.getKey(), emitirCheque(e.getValue(), sumaChequeRef.get(e.getKey()), provNombre, etiquetaPago, usuario));
        }

        // 4) Detalles (uno por línea) apuntando al movimiento consolidado + evento, y saldar cada solicitud.
        for (SolicitudConLineas p : pagos) {
            SolicitudPago sp = spById.get(p.getSolicitudId());
            for (LineaPago l : p.getLineas()) {
                PagoSolicitudDetalle det = new PagoSolicitudDetalle();
                det.setSolicitudPagoId(sp.getId());
                det.setPagoId(pago.getId());
                det.setFuente(l.getFuente());
                det.setCajaVirtualId(l.getCajaVirtualId());
                det.setCuentaBancariaId(l.getCuentaBancariaId());
                det.setMonedaId(l.getMonedaId());
                det.setCotizacion(l.getCotizacion());
                det.setMontoSolicitud(l.getMontoSolicitud());
                det.setUsuario(usuario);
                det.setAnulado(false);
                det.setDescuento(Boolean.TRUE.equals(l.getDescuento()));
                det.setAumento(Boolean.TRUE.equals(l.getAumento()));

                if (l.getFuente() == FuentePago.AJUSTE) {
                    det.setMonto(l.getMonto() != null ? l.getMonto() : BigDecimal.ZERO);
                } else if (l.getFuente() == FuentePago.CHEQUE) {
                    det.setMonto(l.getMonto());
                    if (det.getMontoSolicitud() == null) det.setMontoSolicitud(l.getMonto());
                    Cheque cheque = chequePorRef.get(refDeLinea.get(l));
                    if (cheque != null) {
                        det.setChequeId(cheque.getId());
                        det.setMovimientoBancarioId(cheque.getMovimientoBancarioId()); // no null solo si contado
                    }
                } else {
                    det.setMonto(l.getMonto());
                    if (det.getMontoSolicitud() == null) det.setMontoSolicitud(l.getMonto());
                    GrupoMov g = grupos.get(claveGrupo(l));
                    if (l.getFuente() == FuentePago.CAJA_MAYOR) det.setMovimientoCajaVirtualId(g.movimientoId);
                    else det.setMovimientoBancarioId(g.movimientoId);
                }
                detalleRepository.save(det);
            }

            BigDecimal total = BigDecimal.valueOf(sp.getMontoTotal() != null ? sp.getMontoTotal() : 0.0);
            BigDecimal pagado = sp.getMontoPagado() != null ? sp.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal nuevoPagado = pagado.add(aplicadoById.get(sp.getId()));
            sp.setMontoPagado(nuevoPagado);
            sp.setPago(pago);
            boolean concluido = nuevoPagado.subtract(total).abs().compareTo(TOLERANCIA) <= 0
                    || nuevoPagado.compareTo(total) >= 0;
            if (concluido) {
                // Persistir monto/pago y delegar la transición a CONCLUIDO en el servicio, que
                // además marca las notas de recepción como pagadas (marcarNotasComoPagadas).
                // La rama PARCIAL NO pasa por actualizarEstado: PARCIAL→PARCIAL no es transición válida.
                solicitudPagoService.save(sp);
                solicitudPagoService.actualizarEstado(sp.getId(), SolicitudPagoEstado.CONCLUIDO);
            } else {
                sp.setEstado(SolicitudPagoEstado.PARCIAL);
                solicitudPagoService.save(sp);
            }
            // Si el gasto vino de un PreGasto (workflow de aprobación), sincronizar su estado.
            preGastoService.sincronizarDesdeSolicitudPago(sp);
            // Si la obligación era de un vale de RRHH, dejarlo CONFIRMADO (es lo que mira la
            // liquidación para descontarlo del sueldo).
            valeService.sincronizarDesdeSolicitudPago(sp);
        }

        // PagoService.save fuerza ABIERTO en el alta; marcar el evento como CONCLUIDO ya con id.
        pago.setEstado(PagoEstado.CONCLUIDO);
        return pagoService.save(pago);
    }

    /** Emite un cheque físico (total = suma de sus partes). Diferido reserva saldo; contado debita. */
    private Cheque emitirCheque(LineaPago l, BigDecimal total, String provNombre, String etiquetaPago, Usuario usuario) {
        if (l.getChequeraId() == null) throw new GraphQLException("La forma de pago con cheque requiere una chequera");
        Chequera chequera = chequeraRepository.findById(l.getChequeraId())
                .orElseThrow(() -> new GraphQLException("Chequera no encontrada"));
        Cheque cheque = new Cheque();
        cheque.setChequera(chequera);
        if (l.getMonedaId() != null) cheque.setMoneda(monedaRepository.findById(l.getMonedaId()).orElse(null));
        cheque.setTotal(total != null ? total.doubleValue() : 0.0);
        cheque.setDiferido(Boolean.TRUE.equals(l.getDiferido()));
        cheque.setFechaPago(l.getFechaPago());
        cheque.setFechaEntrega(l.getFechaEmision() != null ? l.getFechaEmision() : java.time.LocalDateTime.now());
        cheque.setBeneficiario(l.getBeneficiario() != null ? l.getBeneficiario() : provNombre);
        cheque.setNominal(l.getNominal() != null ? l.getNominal() : Boolean.TRUE);
        cheque.setConcepto(etiquetaPago);
        return chequeGestionService.emitir(cheque, usuario);
    }

    // ── Anulación por evento ──

    /**
     * Anula un evento de pago completo: revierte cada movimiento consolidado una sola vez,
     * marca todos sus detalles como anulados y reabre todas sus solicitudes. Atómico.
     */
    @Transactional
    public Pago anularPagoCpp(Long pagoId, String motivo, Usuario usuario) {
        Pago pago = pagoService.findById(pagoId)
                .orElseThrow(() -> new GraphQLException("Pago no encontrado: " + pagoId));
        if (pago.getEstado() == PagoEstado.CANCELADO) throw new GraphQLException("El pago ya está anulado");

        List<PagoSolicitudDetalle> detalles = detalleRepository.findByPagoIdOrderByCreadoEnAsc(pagoId)
                .stream().filter(d -> !Boolean.TRUE.equals(d.getAnulado())).collect(Collectors.toList());
        if (detalles.isEmpty()) throw new GraphQLException("El pago no tiene detalles activos para anular");
        String razon = (motivo != null && !motivo.trim().isEmpty()) ? motivo : "Anulación pago #" + pagoId;

        // Revertir cada movimiento consolidado una sola vez (varios detalles comparten el mismo movimiento).
        Set<Long> cajaRevertidas = new HashSet<>();
        Set<Long> bancoRevertidas = new HashSet<>();
        Map<Long, BigDecimal> revertidoPorSolicitud = new HashMap<>();
        for (PagoSolicitudDetalle d : detalles) {
            if (d.getChequeId() != null) {
                // Cheque: diferido libera la reserva; contado revierte su movimiento bancario (no bloquea).
                chequeGestionService.anularPorPago(d.getChequeId(), razon, usuario);
            } else if (d.getMovimientoCajaVirtualId() != null && cajaRevertidas.add(d.getMovimientoCajaVirtualId())) {
                tesoreriaService.revertir(tesoreriaService.findMovimiento(d.getMovimientoCajaVirtualId()), razon, usuario);
            } else if (d.getMovimientoBancarioId() != null && bancoRevertidas.add(d.getMovimientoBancarioId())) {
                movimientoBancarioRepository.findById(d.getMovimientoBancarioId())
                        .ifPresent(mb -> bancoLedgerService.revertir(mb, razon, usuario));
            }
            d.setAnulado(true);
            detalleRepository.save(d);
            BigDecimal montoSol = d.getMontoSolicitud() != null ? d.getMontoSolicitud() : d.getMonto();
            revertidoPorSolicitud.merge(d.getSolicitudPagoId(), aporte(montoSol, d.getAumento()), BigDecimal::add);
        }

        // Reabrir cada solicitud afectada.
        for (Map.Entry<Long, BigDecimal> e : revertidoPorSolicitud.entrySet()) {
            SolicitudPago sp = solicitudPagoService.getRepository().lockById(e.getKey()).orElse(null);
            if (sp == null) continue;
            BigDecimal nuevoPagado = (sp.getMontoPagado() != null ? sp.getMontoPagado() : BigDecimal.ZERO).subtract(e.getValue());
            if (nuevoPagado.signum() < 0) nuevoPagado = BigDecimal.ZERO;
            sp.setMontoPagado(nuevoPagado);
            // Al anular el pago vuelve a SOLICITADO (estaba validada), no a PENDIENTE (borrador).
            sp.setEstado(nuevoPagado.signum() <= 0 ? SolicitudPagoEstado.SOLICITADO : SolicitudPagoEstado.PARCIAL);
            solicitudPagoService.save(sp);
            // El pago se revierte: las notas de recepción dejan de estar pagadas
            // (inverso de marcarNotasComoPagadas, aplicado al reabrir la solicitud).
            solicitudPagoService.desmarcarNotasComoPagadas(sp.getId());
            // Si es un gasto con PreGasto, revertir su estado (PAGADO → ENVIADO_A_TESORERIA).
            preGastoService.sincronizarDesdeSolicitudPago(sp);
            // Si era el pago de un vale, vuelve a quedar pendiente de entrega (CONFIRMADO → SOLICITADO).
            valeService.sincronizarDesdeSolicitudPago(sp);
        }

        pago.setEstado(PagoEstado.CANCELADO);
        return pagoService.save(pago);
    }
}
