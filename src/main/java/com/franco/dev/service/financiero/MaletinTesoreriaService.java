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

    private void req(boolean cond, String msg) { if (!cond) throw new GraphQLException(msg); }

    /** Item del valor de un maletín: total por moneda del último conteo de cierre. */
    public static class ValorMaletinItem {
        private final Moneda moneda;
        private BigDecimal total;
        public ValorMaletinItem(Moneda moneda, BigDecimal total) { this.moneda = moneda; this.total = total; }
        public Moneda getMoneda() { return moneda; }
        public BigDecimal getTotal() { return total; }
        void add(BigDecimal x) { this.total = this.total.add(x); }
    }

    /**
     * Valor físico estimado dentro del maletín: total por moneda del último conteo de cierre
     * de la caja que lo usó. Si el maletín está en una caja abierta (sin cierre) devuelve vacío
     * (el dinero está en la caja, no en el maletín).
     */
    public List<ValorMaletinItem> valorMaletin(Long maletinId) {
        PdvCaja caja = pdvCajaService.findLastByMaletinId(maletinId);
        if (caja == null || caja.getConteoCierre() == null) return new ArrayList<>();
        List<ConteoMoneda> conteo = conteoMonedaService.findByConteoId(
                caja.getConteoCierre().getId(), caja.getSucursalId());
        Map<Long, ValorMaletinItem> porMoneda = new LinkedHashMap<>();
        for (ConteoMoneda cm : conteo) {
            MonedaBilletes mb = cm.getMonedaBilletes();
            if (mb == null || mb.getMoneda() == null || cm.getCantidad() == null || mb.getValor() == null) continue;
            Moneda m = mb.getMoneda();
            BigDecimal aporte = BigDecimal.valueOf(cm.getCantidad()).multiply(BigDecimal.valueOf(mb.getValor()));
            porMoneda.computeIfAbsent(m.getId(), k -> new ValorMaletinItem(m, BigDecimal.ZERO)).add(aporte);
        }
        return new ArrayList<>(porMoneda.values());
    }

    /** Ingresa a la caja mayor el valor que llega dentro de un maletín. */
    @Transactional
    public MovimientoCajaVirtual ingresarMaletin(Long cajaVirtualId, Long maletinId, Long monedaId,
                                                 BigDecimal monto, String descripcion, Usuario usuario) {
        return postear(cajaVirtualId, maletinId, monedaId, monto,
                CajaVirtualTipoMovimiento.INGRESO, "INGRESO MALETIN", descripcion, usuario);
    }

    /**
     * Ingresa a la caja mayor, en una sola operación, el valor del último cierre del maletín
     * para las monedas indicadas (o todas si {@code monedaIds} es nulo/vacío). Los montos se
     * toman del cierre (autoritativo), no del cliente: el usuario solo elige qué monedas incluir.
     * Crea un movimiento de INGRESO por cada moneda con valor.
     */
    @Transactional
    public List<MovimientoCajaVirtual> ingresarMaletinCierre(Long cajaVirtualId, Long maletinId,
                                                             List<Long> monedaIds, String descripcion, Usuario usuario) {
        List<ValorMaletinItem> valores = valorMaletin(maletinId);
        if (valores.isEmpty()) {
            throw new GraphQLException("El maletín no tiene un cierre con valores para ingresar");
        }
        List<MovimientoCajaVirtual> creados = new ArrayList<>();
        for (ValorMaletinItem v : valores) {
            boolean incluir = (monedaIds == null || monedaIds.isEmpty()) || monedaIds.contains(v.getMoneda().getId());
            if (!incluir) continue;
            if (v.getTotal() == null || v.getTotal().signum() <= 0) continue;
            creados.add(postear(cajaVirtualId, maletinId, v.getMoneda().getId(), v.getTotal(),
                    CajaVirtualTipoMovimiento.INGRESO, "INGRESO MALETIN", descripcion, usuario));
        }
        if (creados.isEmpty()) throw new GraphQLException("Seleccione al menos una moneda con valor para ingresar");
        return creados;
    }

    /** Egresa de la caja mayor el valor que se despacha dentro de un maletín. */
    @Transactional
    public MovimientoCajaVirtual egresarMaletin(Long cajaVirtualId, Long maletinId, Long monedaId,
                                                BigDecimal monto, String descripcion, Usuario usuario) {
        return postear(cajaVirtualId, maletinId, monedaId, monto,
                CajaVirtualTipoMovimiento.EGRESO, "EGRESO MALETIN", descripcion, usuario);
    }

    private MovimientoCajaVirtual postear(Long cajaVirtualId, Long maletinId, Long monedaId, BigDecimal monto,
                                          CajaVirtualTipoMovimiento tipo, String prefijo, String descripcion, Usuario usuario) {
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
        m.setReferenciaId(maletinId);
        m.setOrigenTipo(OrigenMovimientoTipo.MALETIN);
        m.setOrigenId(maletinId);
        return tesoreriaService.registrar(m);
    }
}
