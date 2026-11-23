package com.franco.dev.eventos;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proyecciones de los eventos del central (srv #587, C1) contra el catalogo y la lista blanca. */
class EventosCentralTest {

    private static Venta venta(VentaEstado estado, LocalDateTime cierre) {
        Venta v = new Venta();
        v.setId(55L);
        v.setSucursalId(7L);
        v.setEstado(estado);
        v.setTotalGs(144000.0);
        v.setCreadoEn(LocalDateTime.of(2026, 11, 9, 10, 0));
        PdvCaja c = new PdvCaja();
        c.setId(900L);
        c.setFechaCierre(cierre);
        v.setCaja(c);
        return v;
    }

    private static List<VentaTarjeta> tarjetas() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(3L);
        vt.setEstado("CANCELADO");
        vt.setMonto(new BigDecimal("144000"));
        vt.setCodigoAutorizacion("A1B2");
        vt.setNumeroBoleta("000123");
        return Collections.singletonList(vt);
    }

    private static Usuario quien() {
        Usuario u = new Usuario();
        u.setId(12L);
        u.setNickname("ana");
        return u;
    }

    @Test
    void tipoSegunLaTransicion() {
        assertEquals("venta.cancelada", EventosCentral.tipoCancelacion(venta(VentaEstado.CANCELADA, null)));
        assertEquals("venta.reactivada", EventosCentral.tipoCancelacion(venta(VentaEstado.CONCLUIDA, null)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ventaCanceladaTraeLaCajaYSiYaEstabaCerrada() {
        Map<String, Object> d = EventosCentral.ventaCancelada(venta(VentaEstado.CANCELADA, LocalDateTime.of(2026, 11, 9, 18, 0)), tarjetas(), quien());
        assertEquals(900L, ((Map<String, Object>) d.get("caja")).get("id"));
        assertEquals("2026-11-09T18:00", ((Map<String, Object>) d.get("caja")).get("fechaCierre"));
        assertEquals(true, d.get("cajaCerrada"));
        Map<String, Object> a = EventosCentral.atributosVentaCancelada(venta(VentaEstado.CANCELADA, null));
        assertEquals(false, a.get("caja_cerrada"));
        assertEquals(900L, a.get("caja_id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinPermisosNoSalenNiElNickNiLosCodigosDeLaTarjeta() {
        Map<String, Object> d = Proyeccion.limpiar("venta.cancelada",
                EventosCentral.ventaCancelada(venta(VentaEstado.CANCELADA, null), tarjetas(), quien()), false, false);
        assertNull(((Map<String, Object>) d.get("canceladoPor")).get("nickname"));
        assertEquals(12L, ((Map<String, Object>) d.get("canceladoPor")).get("id"));
        Map<String, Object> t = ((List<Map<String, Object>>) d.get("tarjetas")).get(0);
        assertFalse(t.containsKey("codigoAutorizacion"));
        assertFalse(t.containsKey("numeroBoleta"));
        assertEquals("CANCELADO", t.get("estado"));

        Map<String, Object> con = Proyeccion.limpiar("venta.cancelada",
                EventosCentral.ventaCancelada(venta(VentaEstado.CANCELADA, null), tarjetas(), quien()), true, true);
        assertEquals("ana", ((Map<String, Object>) con.get("canceladoPor")).get("nickname"));
        assertEquals("A1B2", ((List<Map<String, Object>>) con.get("tarjetas")).get(0).get("codigoAutorizacion"));
    }

    @Test
    void todoLoQueArmaElCentralEstaEnElCatalogo() {
        Map<String, Object> d = EventosCentral.ventaCancelada(venta(VentaEstado.CANCELADA, null), tarjetas(), quien());
        assertTrue(Proyeccion.rutasFueraDeCatalogo("venta.cancelada", d).isEmpty(),
                Proyeccion.rutasFueraDeCatalogo("venta.cancelada", d).toString());
    }
}
