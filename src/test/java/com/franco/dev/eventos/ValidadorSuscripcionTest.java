package com.franco.dev.eventos;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validacion de suscripciones contra el catalogo en el central (srv #587, C1). */
class ValidadorSuscripcionTest {

    private static final List<String> HOSTS = Collections.singletonList("receptor.ejemplo.com");
    private static final ValidadorSuscripcion.Permisos SIN = new ValidadorSuscripcion.Permisos(false, false);
    private static final ValidadorSuscripcion.Permisos TODO = new ValidadorSuscripcion.Permisos(true, true);

    private static Suscripcion base() {
        Suscripcion s = new Suscripcion();
        s.setUrl("https://receptor.ejemplo.com/frc");
        s.setEventos(Arrays.asList("venta.cancelada", "caja.*"));
        return s;
    }

    @Test
    void laConfiguracionDelClienteEsValida() {
        Suscripcion s = base();
        s.setEventos(Arrays.asList("venta.concluida", "venta.cancelada", "venta_tarjeta.*"));
        s.setEntrega("lote:300");
        Map<String, Map<String, List<Object>>> f = new HashMap<>();
        f.put("venta.concluida", Collections.singletonMap("formas_pago_id", Arrays.asList(2, 4)));
        s.setFiltros(f);
        s.setCampos(Collections.singletonMap("venta.concluida", Arrays.asList("id", "cobro")));
        assertEquals(Collections.emptyList(), ValidadorSuscripcion.validar(s, SIN, HOSTS));
    }

    @Test
    void rechazaEventoAtributoYCampoInexistentes() {
        Suscripcion s = base();
        s.setEventos(Arrays.asList("venta.inventada", "nada.*"));
        s.setFiltros(Collections.singletonMap("venta.concluida", Collections.singletonMap("color", Collections.singletonList("rojo"))));
        s.setCampos(Collections.singletonMap("venta.cancelada", Collections.singletonList("caja.inventado")));
        List<String> e = ValidadorSuscripcion.validar(s, SIN, HOSTS);
        assertTrue(e.stream().anyMatch(x -> x.contains("venta.inventada")), e.toString());
        assertTrue(e.stream().anyMatch(x -> x.contains("nada.*")), e.toString());
        assertTrue(e.stream().anyMatch(x -> x.contains("atributo de filtro color")), e.toString());
        assertTrue(e.stream().anyMatch(x -> x.contains("caja.inventado")), e.toString());
    }

    @Test
    void lopersonalYLoSensibleNecesitanPermiso() {
        Suscripcion s = base();
        s.setCampos(Collections.singletonMap("venta.cancelada", Arrays.asList("canceladoPor.nickname", "tarjetas.codigoAutorizacion")));
        List<String> e = ValidadorSuscripcion.validar(s, SIN, HOSTS);
        assertTrue(e.stream().anyMatch(x -> x.contains("es personal")), e.toString());
        assertTrue(e.stream().anyMatch(x -> x.contains("es sensible")), e.toString());

        s.setDatosPersonales(true);
        s.setDatosSensibles(true);
        e = ValidadorSuscripcion.validar(s, SIN, HOSTS);
        assertTrue(e.stream().anyMatch(x -> x.contains("permiso para datos personales")), e.toString());
        assertTrue(e.stream().anyMatch(x -> x.contains("permiso para datos sensibles")), e.toString());
        assertEquals(Collections.emptyList(), ValidadorSuscripcion.validar(s, TODO, HOSTS));
    }

    @Test
    void urlSoloHttpsYSinIpInterna() {
        Suscripcion s = base();
        s.setUrl("http://receptor.ejemplo.com/frc");
        assertTrue(ValidadorSuscripcion.validar(s, SIN, HOSTS).stream().anyMatch(x -> x.contains("https")));
        s.setUrl("https://127.0.0.1/frc");
        assertTrue(ValidadorSuscripcion.validar(s, SIN, Collections.emptyList()).stream().anyMatch(x -> x.contains("IP interna")));
        s.setUrl("https://10.0.0.5/frc");
        assertTrue(ValidadorSuscripcion.validar(s, SIN, Collections.emptyList()).stream().anyMatch(x -> x.contains("IP interna")));
    }

    @Test
    void entregaInvalida() {
        Suscripcion s = base();
        s.setEntrega("cada-tanto");
        assertTrue(ValidadorSuscripcion.validar(s, SIN, HOSTS).stream().anyMatch(x -> x.contains("Entrega invalida")));
    }

    @Test
    void elCatalogoSabeQueEmiteElCentral() {
        assertEquals("central", CatalogoEventos.nodo("venta.cancelada"));
        assertEquals("central", CatalogoEventos.nodo("retiro.cancelado"));
        assertEquals("central", CatalogoEventos.nodo("gasto.cancelado"));
        assertEquals("filial", CatalogoEventos.nodo("venta.concluida"));
        assertEquals("filial", CatalogoEventos.nodo("caja.cerrada"));
    }
}
