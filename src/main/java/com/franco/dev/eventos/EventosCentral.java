package com.franco.dev.eventos;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Usuario;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proyecciones de los eventos que ocurren en el central (srv #587, C1): venta cancelada o reactivada.
 * Retiro y gasto usan las mismas proyecciones de la filial (EventosRetiroGasto del central).
 */
public final class EventosCentral {

    private EventosCentral() {
    }

    /** venta.cancelada si quedo CANCELADA, venta.reactivada si volvio a CONCLUIDA. */
    public static String tipoCancelacion(Venta v) {
        return v.getEstado() == VentaEstado.CANCELADA ? "venta.cancelada" : "venta.reactivada";
    }

    public static Map<String, Object> ventaCancelada(Venta v, List<VentaTarjeta> tarjetas, Usuario quien) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", v.getId());
        d.put("estado", v.getEstado() != null ? v.getEstado().name() : null);
        d.put("sucursalId", v.getSucursalId());
        d.put("sucursal", EventosVenta.mapa("id", v.getSucursalId()));
        d.put("creadoEn", v.getCreadoEn() != null ? v.getCreadoEn().toString() : null);
        d.put("totalGs", v.getTotalGs());
        PdvCaja c = v.getCaja();
        Map<String, Object> caja = new LinkedHashMap<>();
        caja.put("id", c != null ? c.getId() : null);
        caja.put("fechaCierre", c != null && c.getFechaCierre() != null ? c.getFechaCierre().toString() : null);
        d.put("caja", caja);
        // Lo que pidio el cliente: si la caja ya estaba cerrada, su total cambia despues del cierre
        d.put("cajaCerrada", c != null && c.getFechaCierre() != null);
        Map<String, Object> por = new LinkedHashMap<>();
        por.put("id", quien != null ? quien.getId() : null);
        por.put("nickname", quien != null ? quien.getNickname() : null);
        d.put("canceladoPor", por);
        d.put("canceladoEn", LocalDateTime.now().toString());
        List<Map<String, Object>> lt = new ArrayList<>();
        if (tarjetas != null) {
            for (VentaTarjeta vt : tarjetas) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("id", vt.getId());
                t.put("estado", vt.getEstado());
                t.put("monto", vt.getMonto());
                t.put("moneda", EventosVenta.mapa("id", vt.getMoneda() != null ? vt.getMoneda().getId() : null));
                t.put("terminalPos", EventosVenta.mapa("id", vt.getTerminalPos() != null ? vt.getTerminalPos().getId() : null));
                t.put("codigoAutorizacion", vt.getCodigoAutorizacion());
                t.put("numeroBoleta", vt.getNumeroBoleta());
                lt.add(t);
            }
        }
        d.put("tarjetas", lt);
        return d;
    }

    public static Map<String, Object> atributosVentaCancelada(Venta v) {
        Map<String, Object> a = new LinkedHashMap<>();
        PdvCaja c = v.getCaja();
        a.put("caja_id", c != null ? c.getId() : null);
        a.put("caja_cerrada", c != null && c.getFechaCierre() != null);
        a.put("monto_gs", v.getTotalGs());
        return a;
    }
    /** retiro.cancelado / retiro.reactivado: la misma forma que el retiro.creado de la filial. */
    public static Map<String, Object> retiro(com.franco.dev.domain.financiero.Retiro retiro,
                                             List<com.franco.dev.domain.financiero.RetiroDetalle> detalles) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", retiro.getId());
        d.put("sucursalId", retiro.getSucursalId());
        d.put("creadoEn", retiro.getCreadoEn() != null ? retiro.getCreadoEn().toString() : null);
        d.put("estado", retiro.getEstado() != null ? retiro.getEstado().name() : null);
        d.put("observacion", retiro.getObservacion());
        if (retiro.getCajaSalida() != null) {
            d.put("caja", EventosVenta.mapa("id", retiro.getCajaSalida().getId()));
            Map<String, Object> cs = EventosVenta.mapa("id", retiro.getCajaSalida().getId());
            if (retiro.getCajaSalida().getMaletin() != null)
                cs.put("maletin", EventosVenta.mapa("descripcion", retiro.getCajaSalida().getMaletin().getDescripcion()));
            d.put("cajaSalida", cs);
        }
        if (retiro.getUsuario() != null) d.put("usuario", EventosVenta.usuario(retiro.getUsuario()));
        if (retiro.getResponsable() != null && retiro.getResponsable().getPersona() != null)
            d.put("responsable", EventosVenta.mapa("persona", EventosVenta.mapa("nombre", retiro.getResponsable().getPersona().getNombre())));
        List<Map<String, Object>> det = new ArrayList<>();
        if (detalles != null) for (com.franco.dev.domain.financiero.RetiroDetalle rd : detalles) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cantidad", rd.getCantidad());
            if (rd.getMoneda() != null) {
                Map<String, Object> mo = EventosVenta.mapa("id", rd.getMoneda().getId());
                mo.put("denominacion", rd.getMoneda().getDenominacion());
                m.put("moneda", mo);
            }
            det.add(m);
        }
        d.put("retiroDetalleList", det);
        return d;
    }

    public static Map<String, Object> atributosRetiro(com.franco.dev.domain.financiero.Retiro retiro,
                                                      List<com.franco.dev.domain.financiero.RetiroDetalle> detalles) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("caja_id", retiro.getCajaSalida() != null ? retiro.getCajaSalida().getId() : null);
        Double monto = null;
        if (detalles != null) for (com.franco.dev.domain.financiero.RetiroDetalle rd : detalles)
            if (rd.getMoneda() != null && rd.getMoneda().getDenominacion() != null
                    && rd.getMoneda().getDenominacion().toUpperCase().startsWith("GUARANI")) monto = rd.getCantidad();
        a.put("monto_gs", monto);
        return a;
    }
    /** gasto.cancelado: la misma forma que el gasto.creado de la filial. */
    public static Map<String, Object> gasto(com.franco.dev.domain.financiero.Gasto g) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", g.getId());
        if (g.getCaja() != null) d.put("caja", EventosVenta.mapa("id", g.getCaja().getId()));
        d.put("sucursalId", g.getSucursalId());
        if (g.getTipoGasto() != null) {
            Map<String, Object> t = EventosVenta.mapa("id", g.getTipoGasto().getId());
            t.put("descripcion", g.getTipoGasto().getDescripcion());
            d.put("tipoGasto", t);
        }
        d.put("valorGs", g.getRetiroGs());
        d.put("valorRs", g.getRetiroRs());
        d.put("valorDs", g.getRetiroDs());
        d.put("sucursal", EventosVenta.mapa("id", g.getSucursalId()));
        d.put("observacion", g.getObservacion());
        d.put("cancelado", g.getCancelado());
        d.put("retiroGs", g.getRetiroGs());
        d.put("retiroRs", g.getRetiroRs());
        d.put("retiroDs", g.getRetiroDs());
        d.put("vueltoGs", g.getVueltoGs());
        d.put("vueltoRs", g.getVueltoRs());
        d.put("vueltoDs", g.getVueltoDs());
        if (g.getResponsable() != null && g.getResponsable().getPersona() != null)
            d.put("responsable", EventosVenta.mapa("persona", EventosVenta.mapa("nombre", g.getResponsable().getPersona().getNombre())));
        if (g.getUsuario() != null) d.put("usuario", EventosVenta.usuario(g.getUsuario()));
        d.put("creadoEn", g.getCreadoEn() != null ? g.getCreadoEn().toString() : null);
        return d;
    }

    public static Map<String, Object> atributosGasto(com.franco.dev.domain.financiero.Gasto g) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("caja_id", g.getCaja() != null ? g.getCaja().getId() : null);
        a.put("tipo_gasto_id", g.getTipoGasto() != null ? g.getTipoGasto().getId() : null);
        a.put("monto_gs", g.getRetiroGs());
        return a;
    }
}
