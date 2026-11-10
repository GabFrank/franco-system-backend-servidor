package com.franco.dev.eventos;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** venta_tarjeta.completada desde la app con las lineas de cobro ya armadas (srv #587). */
class EventosTarjetaLineasTest {

    private static VentaTarjeta vt() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(22L);
        vt.setSucursalId(2L);
        vt.setEstado("COMPLETADO");
        return vt;
    }

    private static Venta venta() {
        Venta v = new Venta();
        v.setId(80098L);
        v.setEstado(VentaEstado.CONCLUIDA);
        return v;
    }

    @Test
    @SuppressWarnings("unchecked")
    void lasLineasVanEnVentaCobro() {
        Map<String, Object> linea = Map.of("id", 115116L, "formaPago", Map.of("id", 2L), "identificadorTransaccion", "E2E");
        Map<String, Object> d = EventosTarjeta.tarjetaConLineas(vt(), venta(), List.of(linea));
        Map<String, Object> v = (Map<String, Object>) d.get("venta");
        List<Object> l = (List<Object>) ((Map<String, Object>) v.get("cobro")).get("cobroDetalleList");
        assertEquals(List.of(linea), l);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinLineasQuedaListaVacia() {
        Map<String, Object> d = EventosTarjeta.tarjetaConLineas(vt(), venta(), null);
        Map<String, Object> v = (Map<String, Object>) d.get("venta");
        assertTrue(((List<Object>) ((Map<String, Object>) v.get("cobro")).get("cobroDetalleList")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void laFormaViejaConEntidadesSigueIgual() {
        Map<String, Object> d = EventosTarjeta.tarjeta(vt(), venta(), Collections.emptyList());
        Map<String, Object> v = (Map<String, Object>) d.get("venta");
        assertTrue(((List<Object>) ((Map<String, Object>) v.get("cobro")).get("cobroDetalleList")).isEmpty());
    }
}
