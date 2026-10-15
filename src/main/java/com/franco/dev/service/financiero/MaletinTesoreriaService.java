package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puente maletín ↔ caja mayor. El maletín (dominio PDV/caja física) no tiene saldo propio:
 * su "valor" es el total del último conteo de cierre de la {@link PdvCaja} que lo usó (el
 * efectivo que quedó físicamente dentro del maletín). Este servicio permite ingresar ese
 * valor a la caja mayor cuando el maletín llega a tesorería, y egresarlo cuando se despacha.
 */
@Service
@AllArgsConstructor
public class MaletinTesoreriaService {

    private final MaletinService maletinService;
    private final PdvCajaService pdvCajaService;
    private final ConteoMonedaService conteoMonedaService;
    private final CajaVirtualRepository cajaVirtualRepository;
    private final MonedaRepository monedaRepository;
    private final TesoreriaService tesoreriaService;
    private final BloqueoTransaccionalService bloqueo;
    private final com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository movimientoRepository;

    private void req(boolean cond, String msg) { if (!cond) throw new GraphQLException(msg); }

    /** Item del valor de un maletín: total por moneda del último conteo de cierre. */
    public static class ValorMaletinItem {
        private final Moneda moneda;
        private BigDecimal total;
        /** El cierre ya se ingresó a la caja mayor en esta moneda (hay un ingreso activo que lo marca). */
        private boolean ingresado;
        public ValorMaletinItem(Moneda moneda, BigDecimal total) { this.moneda = moneda; this.total = total; }
        public Moneda getMoneda() { return moneda; }
        public BigDecimal getTotal() { return total; }
        public Boolean getIngresado() { return ingresado; }
        void add(BigDecimal x) { this.total = this.total.add(x); }
    }

    /** El último cierre de un maletín: la caja de PDV que lo usó y lo que quedó adentro, por moneda. */
    private static class Cierre {
        final PdvCaja caja;
        final List<ValorMaletinItem> valores;
        Cierre(PdvCaja caja, List<ValorMaletinItem> valores) { this.caja = caja; this.valores = valores; }
        boolean enCajaAbierta() { return caja != null && caja.getConteoCierre() == null; }
    }

    /**
     * La caja y los valores se resuelven juntos, una sola vez: el ingreso marca el cierre con la caja, y
     * tiene que ser la misma de la que salieron los montos. Los valores van por moneda ascendente.
     */
    private Cierre cierreDe(Long maletinId) {
        PdvCaja caja = pdvCajaService.findLastByMaletinId(maletinId);
        if (caja == null || caja.getConteoCierre() == null) return new Cierre(caja, new ArrayList<>());
        List<ConteoMoneda> conteo = conteoMonedaService.findByConteoId(
                caja.getConteoCierre().getId(), caja.getSucursalId());
        Map<Long, ValorMaletinItem> porMoneda = new java.util.TreeMap<>();
        for (ConteoMoneda cm : conteo) {
            MonedaBilletes mb = cm.getMonedaBilletes();
            if (mb == null || mb.getMoneda() == null || cm.getCantidad() == null || mb.getValor() == null) continue;
            Moneda m = mb.getMoneda();
            BigDecimal aporte = BigDecimal.valueOf(cm.getCantidad()).multiply(BigDecimal.valueOf(mb.getValor()));
            porMoneda.computeIfAbsent(m.getId(), k -> new ValorMaletinItem(m, BigDecimal.ZERO)).add(aporte);
        }
        return new Cierre(caja, new ArrayList<>(porMoneda.values()));
    }

    /** Los ingresos activos que marcan que este cierre ya entró a la caja mayor en esta moneda. */
    private List<Long> ingresosDe(Long maletinId, PdvCaja caja, Long monedaId) {
        if (caja == null || caja.getId() == null || caja.getSucursalId() == null) return new ArrayList<>();
        return movimientoRepository.findIngresosDeCierreDeMaletin(maletinId, caja.getId(), caja.getSucursalId(), monedaId);
    }

    /**
     * Valor físico estimado dentro del maletín: total por moneda del último conteo de cierre
     * de la caja que lo usó. Si el maletín está en una caja abierta (sin cierre) devuelve vacío
     * (el dinero está en la caja, no en el maletín). Cada moneda dice si ese cierre ya se ingresó.
     */
    public List<ValorMaletinItem> valorMaletin(Long maletinId) {
        Cierre cierre = cierreDe(maletinId);
        for (ValorMaletinItem v : cierre.valores) {
            v.ingresado = !ingresosDe(maletinId, cierre.caja, v.getMoneda().getId()).isEmpty();
        }
        return cierre.valores;
    }

    /** Ingresa a la caja mayor el valor que llega dentro de un maletín. */
    @Transactional
    public MovimientoCajaVirtual ingresarMaletin(Long cajaVirtualId, Long maletinId, Long monedaId,
                                                 BigDecimal monto, String descripcion, Usuario usuario) {
        return postear(cajaVirtualId, maletinId, monedaId, monto,
                CajaVirtualTipoMovimiento.INGRESO, "INGRESO MALETIN", descripcion, usuario, null);
    }

    /**
     * Ingresa a la caja mayor, en una sola operación, el valor del último cierre del maletín
     * para las monedas indicadas (o todas si {@code monedaIds} es nulo/vacío). Los montos se
     * toman del cierre (autoritativo), no del cliente: el usuario solo elige qué monedas incluir.
     * Crea un movimiento de INGRESO por cada moneda con valor.
     *
     * <p><b>Un cierre se ingresa una sola vez por moneda</b> (issue #376). Cada ingreso queda marcado con la
     * caja de PDV del cierre ({@code referencia_id} + {@code origen_sucursal_id}); mientras ese movimiento
     * siga activo, el mismo cierre no vuelve a entrar. Anularlo desde la caja mayor lo habilita de nuevo.
     * Antes no quedaba ninguna marca y repetir el pedido ingresaba la misma plata otra vez.</p>
     *
     * <p>El lock es por nombre y no sobre la fila del maletín: esa tabla llega por replicación desde la
     * filial. Va antes de resolver el cierre.</p>
     */
    @Transactional
    public List<MovimientoCajaVirtual> ingresarMaletinCierre(Long cajaVirtualId, Long maletinId,
                                                             List<Long> monedaIds, String descripcion, Usuario usuario) {
        bloqueo.tomar("MALETIN_CIERRE:" + maletinId);
        Cierre cierre = cierreDe(maletinId);
        if (cierre.enCajaAbierta()) {
            throw new GraphQLException("El maletín está en una caja abierta: todavía no tiene un cierre para ingresar");
        }
        if (cierre.valores.isEmpty()) {
            throw new GraphQLException("El maletín no tiene un cierre con valores para ingresar");
        }
        boolean todas = monedaIds == null || monedaIds.isEmpty();
        List<ValorMaletinItem> aIngresar = new ArrayList<>();
        List<String> yaIngresadas = new ArrayList<>();
        for (ValorMaletinItem v : cierre.valores) {
            if (!todas && !monedaIds.contains(v.getMoneda().getId())) continue;
            if (v.getTotal() == null || v.getTotal().signum() <= 0) continue;
            List<Long> previos = ingresosDe(maletinId, cierre.caja, v.getMoneda().getId());
            if (previos.isEmpty()) {
                aIngresar.add(v);
            } else {
                yaIngresadas.add(v.getMoneda().getDenominacion() + " (movimiento #" + previos.get(0) + ")");
            }
        }
        // Pedidas una por una, no entra ninguna si alguna ya entró. Con «todas» se ingresan las que faltan.
        if (!yaIngresadas.isEmpty() && (!todas || aIngresar.isEmpty())) {
            throw new GraphQLException("El cierre de este maletín ya se ingresó en "
                    + String.join(", ", yaIngresadas) + ".");
        }
        if (aIngresar.isEmpty()) throw new GraphQLException("Seleccione al menos una moneda con valor para ingresar");

        List<MovimientoCajaVirtual> creados = new ArrayList<>();
        for (ValorMaletinItem v : aIngresar) {
            creados.add(postear(cajaVirtualId, maletinId, v.getMoneda().getId(), v.getTotal(),
                    CajaVirtualTipoMovimiento.INGRESO, "INGRESO MALETIN", descripcion, usuario, cierre.caja));
        }
        return creados;
    }

    /** Egresa de la caja mayor el valor que se despacha dentro de un maletín. */
    @Transactional
    public MovimientoCajaVirtual egresarMaletin(Long cajaVirtualId, Long maletinId, Long monedaId,
                                                BigDecimal monto, String descripcion, Usuario usuario) {
        return postear(cajaVirtualId, maletinId, monedaId, monto,
                CajaVirtualTipoMovimiento.EGRESO, "EGRESO MALETIN", descripcion, usuario, null);
    }

    /** {@code cierre}: la caja de PDV cuyo cierre se ingresa, o nulo en los movimientos hechos a mano. */
    private MovimientoCajaVirtual postear(Long cajaVirtualId, Long maletinId, Long monedaId, BigDecimal monto,
                                          CajaVirtualTipoMovimiento tipo, String prefijo, String descripcion, Usuario usuario,
                                          PdvCaja cierre) {
        req(monto != null && monto.signum() > 0, "Monto de maletín inválido");
        Maletin maletin = maletinService.findById(maletinId)
                .orElseThrow(() -> new GraphQLException("Maletín no encontrado: " + maletinId));
        CajaVirtual caja = cajaVirtualRepository.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja mayor no encontrada: " + cajaVirtualId));
        Moneda moneda = monedaRepository.findById(monedaId)
                .orElseThrow(() -> new GraphQLException("Moneda no encontrada: " + monedaId));

        MovimientoCajaVirtual m = new MovimientoCajaVirtual();
        m.setCajaVirtual(caja);
        m.setTipoMovimiento(tipo);
        m.setCantidad(monto.doubleValue());
        m.setMoneda(moneda);
        m.setUsuario(usuario);
        m.setDescripcion(prefijo + " " + (maletin.getDescripcion() != null ? maletin.getDescripcion() : ("#" + maletinId))
                + (descripcion != null && !descripcion.trim().isEmpty() ? " - " + descripcion : ""));
        // La marca del cierre: la caja de PDV y su sucursal. A mano, la referencia repite el maletín.
        m.setReferenciaId(cierre != null ? cierre.getId() : maletinId);
        m.setOrigenSucursalId(cierre != null ? cierre.getSucursalId() : null);
        m.setOrigenTipo(OrigenMovimientoTipo.MALETIN);
        m.setOrigenId(maletinId);
        return tesoreriaService.registrar(m);
    }
}
