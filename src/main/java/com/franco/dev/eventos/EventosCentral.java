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
}
