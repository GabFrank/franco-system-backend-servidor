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
    private final com.franco.dev.service.rrhh.LiquidacionSueldoService liquidacionSueldoService;
    private final com.franco.dev.service.rrhh.LiquidacionFinalService liquidacionFinalService;
    private final com.franco.dev.service.rrhh.AguinaldoService aguinaldoService;

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



    /**
     * Desglose de un evento de pago: que documentos se pagaron y cuanto se imputo a cada uno.
     *
     * <p>Es lo que responde la pregunta que el movimiento consolidado no puede contestar solo.
     * Se arma desde {@code PagoSolicitudDetalle}, que ya guarda una fila por documento y linea:
     * se agrupan por solicitud y se suman los {@code montoSolicitud}.</p>
     */
    @Transactional(readOnly = true)
    public List<com.franco.dev.service.financiero.dto.DetallePagoItemDto> detalleDePago(Long pagoId) {
        java.util.LinkedHashMap<Long, java.math.BigDecimal> imputadoPorSolicitud = new java.util.LinkedHashMap<>();
        for (com.franco.dev.domain.financiero.PagoSolicitudDetalle d
                : detalleRepository.findByPagoIdOrderByCreadoEnAsc(pagoId)) {
            if (Boolean.TRUE.equals(d.getAnulado())) continue;
            java.math.BigDecimal aporte = d.getMontoSolicitud() != null ? d.getMontoSolicitud() : java.math.BigDecimal.ZERO;
            imputadoPorSolicitud.merge(d.getSolicitudPagoId(), aporte, java.math.BigDecimal::add);
        }
        List<com.franco.dev.service.financiero.dto.DetallePagoItemDto> out = new ArrayList<>();
        for (java.util.Map.Entry<Long, java.math.BigDecimal> e : imputadoPorSolicitud.entrySet()) {
            SolicitudPago sp = solicitudPagoService.findById(e.getKey()).orElse(null);
            if (sp == null) continue;
            com.franco.dev.service.financiero.dto.DetallePagoItemDto i =
                    new com.franco.dev.service.financiero.dto.DetallePagoItemDto();
            i.setSolicitudPagoId(sp.getId());
            i.setTipo(sp.getTipo() != null ? sp.getTipo().name() : null);
            i.setDescripcion(sp.getObservaciones());
            i.setProveedorNombre(sp.getProveedor() != null && sp.getProveedor().getPersona() != null
                    ? sp.getProveedor().getPersona().getNombre() : null);
            if (sp.getMoneda() != null) {
                i.setMonedaDenominacion(sp.getMoneda().getDenominacion());
                i.setMonedaSimbolo(sp.getMoneda().getSimbolo());
                i.setDecimales(sp.getMoneda().getDecimales());
            }
            i.setMontoImputado(e.getValue());
            i.setMontoTotal(sp.getMontoTotal() != null ? java.math.BigDecimal.valueOf(sp.getMontoTotal()) : null);
            i.setMontoPagado(sp.getMontoPagado());
            i.setEstado(sp.getEstado() != null ? sp.getEstado().name() : null);
            out.add(i);
        }
        return out;
    }

    // ─────────────── Concepto del evento de pago: etiqueta + origen del movimiento ───────────────

    /**
     * Que se esta pagando en este evento. El dialogo agrupa por modo, asi que en la practica un
     * evento tiene un solo concepto; MIXTO existe por defensa (llamadas por API).
     */
    private enum ConceptoEvento { COMPRA, GASTO, VALE, LIQUIDACION, FINIQUITO, AGUINALDO, MIXTO }

    /**
     * Clasifica el evento por el tipo de sus solicitudes. Las de tipo RRHH se desambiguan
     * preguntandole a cada modulo cual es dueno de la obligacion: el tipo RRHH es uno solo para
     * vale, liquidacion, finiquito y aguinaldo.
     */
    private ConceptoEvento clasificar(List<SolicitudPago> sols) {
        ConceptoEvento unico = null;
        for (SolicitudPago sp : sols) {
            ConceptoEvento c;
            if (sp.getTipo() == com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.GASTO) {
                c = ConceptoEvento.GASTO;
            } else if (sp.getTipo() == com.franco.dev.domain.operaciones.enums.TipoSolicitudPago.RRHH) {
                c = conceptoRrhh(sp.getId());
            } else {
                c = ConceptoEvento.COMPRA;
            }
            if (unico == null) unico = c;
            else if (unico != c) return ConceptoEvento.MIXTO;
        }
        return unico != null ? unico : ConceptoEvento.COMPRA;
    }

    /** Cual de los modulos de RRHH es dueno de la obligacion. Vale es el caso mas comun: se pregunta primero. */
    private ConceptoEvento conceptoRrhh(Long solicitudPagoId) {
        if (valeService.tieneSolicitud(solicitudPagoId)) return ConceptoEvento.VALE;
        if (liquidacionSueldoService.tieneSolicitud(solicitudPagoId)) return ConceptoEvento.LIQUIDACION;
        if (liquidacionFinalService.tieneSolicitud(solicitudPagoId)) return ConceptoEvento.FINIQUITO;
        if (aguinaldoService.tieneSolicitud(solicitudPagoId)) return ConceptoEvento.AGUINALDO;
        return ConceptoEvento.VALE;   // obligacion RRHH huerfana: se etiqueta como vale
    }

    /** Origen del movimiento, para que el historial de caja diga el concepto real y no "Compra". */
    private OrigenMovimientoTipo origenDe(ConceptoEvento c) {
        switch (c) {
            case GASTO:       return OrigenMovimientoTipo.GASTO;
            case VALE:        return OrigenMovimientoTipo.RRHH_VALE;
            case LIQUIDACION: return OrigenMovimientoTipo.RRHH_LIQUIDACION_SUELDO;
            case FINIQUITO:   return OrigenMovimientoTipo.RRHH_LIQUIDACION_FINAL;
            case AGUINALDO:   return OrigenMovimientoTipo.RRHH_AGUINALDO;
            default:          return OrigenMovimientoTipo.PAGO_CPP;
        }
    }

    /**
     * Descripcion del movimiento consolidado.
     *
     * <p>Con un solo documento se muestra su descripcion especifica. Con varios <b>no</b> se
     * puede mostrar la de uno solo: seria informacion equivocada sobre un asiento que paga a
     * todos. En ese caso se etiqueta el evento y el desglose se consulta con el detalle del
     * pago (cada documento tiene su fila en {@code PagoSolicitudDetalle}).</p>
     */
    private String etiquetaDe(ConceptoEvento c, List<SolicitudPago> sols, boolean tieneProveedor, String provNombre) {
        int n = sols.size();
        if (n > 1) {
            switch (c) {
                case GASTO:       return "Pago consolidado de " + n + " gastos";
                case VALE:        return "Pago consolidado de " + n + " vales";
                case LIQUIDACION: return "Pago consolidado de " + n + " liquidaciones";
                case FINIQUITO:   return "Pago consolidado de " + n + " finiquitos";
                case AGUINALDO:   return "Pago consolidado de " + n + " aguinaldos";
                case COMPRA:      return (tieneProveedor ? "Pago a " + provNombre : "Pago a proveedor")
                                        + " (" + n + " notas)";
                default:          return "Pago consolidado de " + n + " documentos";
            }
        }
        SolicitudPago sp = sols.isEmpty() ? null : sols.get(0);
        if (sp == null) return tieneProveedor ? ("Pago a " + provNombre) : "Pago";
        if (c == ConceptoEvento.GASTO) {
            // Gasto: descripcion rica → "#id - categoria - beneficiario - descripcion".
            String cat = sp.getTipoGasto() != null ? sp.getTipoGasto().getDescripcion() : "Sin categoría";
            String benef = tieneProveedor ? provNombre : "—";
            String desc = sp.getObservaciones() != null ? sp.getObservaciones() : "";
            return "#" + sp.getId() + " - " + cat + " - " + benef + " - " + desc;
        }
        if (c != ConceptoEvento.COMPRA) {
            // RRHH: la observacion de la solicitud ya trae "VALE #id - FUNCIONARIO - MOTIVO",
            // "LIQUIDACION 2026-07 #id - FUNCIONARIO", etc.
            String desc = sp.getObservaciones();
            if (desc != null && !desc.trim().isEmpty()) return desc;
        }
        return tieneProveedor ? ("Pago a " + provNombre) : "Pago de gasto";
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
        List<SolicitudPago> sols = new ArrayList<>(spById.values());
        ConceptoEvento concepto = clasificar(sols);
        String etiquetaPago = etiquetaDe(concepto, sols, tieneProveedor, provNombre);
        OrigenMovimientoTipo origenPago = origenDe(concepto);
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
                m.setOrigenTipo(origenPago);
                m.setOrigenId(pago.getId());
                g.movimientoId = tesoreriaService.registrar(m).getId();
            } else if (g.fuente == FuentePago.CUENTA_BANCARIA) {
                MovimientoBancario mb = bancoLedgerService.registrar(g.cuentaBancariaId, MovimientoBancarioTipo.SALIDA_MANUAL,
                        g.suma, etiquetaPago, origenPago.name(), pago.getId(), usuario);
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
            // Idem para los demas conceptos de RRHH pagables desde el hub de la caja
            // (liquidacion mensual, finiquito, aguinaldo). Cada uno resuelve por su propio
            // solicitud_pago_id y no hace nada si la obligacion no es suya.
            liquidacionSueldoService.sincronizarDesdeSolicitudPago(sp);
            liquidacionFinalService.sincronizarDesdeSolicitudPago(sp);
            aguinaldoService.sincronizarDesdeSolicitudPago(sp);
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
            // Idem para los demas conceptos de RRHH pagables desde el hub de la caja
            // (liquidacion mensual, finiquito, aguinaldo). Cada uno resuelve por su propio
            // solicitud_pago_id y no hace nada si la obligacion no es suya.
            liquidacionSueldoService.sincronizarDesdeSolicitudPago(sp);
            liquidacionFinalService.sincronizarDesdeSolicitudPago(sp);
            aguinaldoService.sincronizarDesdeSolicitudPago(sp);
        }

        pago.setEstado(PagoEstado.CANCELADO);
        return pagoService.save(pago);
    }
}
