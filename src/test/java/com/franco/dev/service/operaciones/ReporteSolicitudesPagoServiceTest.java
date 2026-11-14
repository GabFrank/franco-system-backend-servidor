package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintImage;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reporte de la lista de solicitudes de pago: armado del contenido (etiquetas, totales por moneda sin las
 * canceladas) y la plantilla solicitudes-pago-lista.jrxml, que se compila recién en runtime.
 */
class ReporteSolicitudesPagoServiceTest {

    private final ReporteSolicitudesPagoService service = new ReporteSolicitudesPagoService(null, null, null, null, null);

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 10, 9, 15);
    private static final String PROVEEDOR_LARGO = "DISTRIBUIDORA DE BEBIDAS Y ALIMENTOS DEL PARAGUAY SOCIEDAD ANONIMA";

    private final Moneda gs = moneda(1L, "GUARANI", "Gs.", 0);
    private final Moneda us = moneda(2L, "DOLAR", "US$", 2);

    @Test
    void totalizaPorMonedaSinContarLasCanceladas() {
        List<SolicitudPago> lista = Arrays.asList(
                solicitud("SP-1", 1500000d, gs, SolicitudPagoEstado.SOLICITADO),
                solicitud("SP-2", 500000d, gs, SolicitudPagoEstado.PARCIAL),
                solicitud("SP-3", 999999d, gs, SolicitudPagoEstado.CANCELADO),
                solicitud("SP-4", 1250.5d, us, SolicitudPagoEstado.CONCLUIDO));

        ReporteSolicitudesPagoService.Contenido c = service.armar(lista, null, null, null,
                "2026-11-01", "2026-11-14", Collections.emptyList(), usuario());

        assertEquals(4, c.filas.size(), "la cancelada se lista");
        assertTrue(c.filas.get(2).getCancelada());
        assertEquals("Cancelada", c.filas.get(2).getEstado());
        assertEquals("Pago Parcial", c.filas.get(1).getEstado());
        assertEquals("Gs. 1.500.000", c.filas.get(0).getMonto());
        assertEquals("US$ 1.250,50", c.filas.get(3).getMonto());
        assertEquals("10/11/26 09:15", c.filas.get(0).getFecha());

        String totales = (String) c.parametros.get("totales");
        assertTrue(totales.contains("GUARANI: Gs. 2.000.000"), totales);
        assertTrue(totales.contains("DOLAR: US$ 1.250,50"), totales);
        assertEquals("01/11/2026", c.parametros.get("desdeFiltro"));
        assertEquals("14/11/2026", c.parametros.get("hastaFiltro"));
        assertEquals("Todas", c.parametros.get("monedaFiltro"));
        assertEquals("Todos", c.parametros.get("proveedorFiltro"));
        assertEquals("Todos", c.parametros.get("estadoFiltro"));
        assertEquals("Todos", c.parametros.get("numeroFiltro"));
        assertEquals("4", c.parametros.get("cantidad"));
    }

    @Test
    void elMontoEsElMontoTotalDeLaSolicitud() {
        // Es el campo que muestra la tabla; el PDF individual recalcula por notas, este no.
        SolicitudPago s = solicitud("SP-9", 777000d, gs, SolicitudPagoEstado.SOLICITADO);
        ReporteSolicitudesPagoService.Contenido c = service.armar(Collections.singletonList(s), null, null, null,
                null, null, Collections.emptyList(), usuario());
        assertEquals("Gs. 777.000", c.filas.get(0).getMonto());
    }

    @Test
    void etiquetasDeLosFiltrosAplicados() {
        ReporteSolicitudesPagoService.Contenido c = service.armar(Collections.emptyList(), proveedor("ACME SA"),
                SolicitudPagoEstado.CONCLUIDO, " sp-1 ", null, "", Arrays.asList(gs, us), usuario());

        assertEquals("ACME SA", c.parametros.get("proveedorFiltro"));
        assertEquals("Pagada", c.parametros.get("estadoFiltro"));
        assertEquals("SP-1", c.parametros.get("numeroFiltro"));
        assertEquals("GUARANI (Gs.), DOLAR (US$)", c.parametros.get("monedaFiltro"));
        assertEquals("Sin límite", c.parametros.get("desdeFiltro"));
        assertEquals("Sin límite", c.parametros.get("hastaFiltro"));
        assertEquals("Sin solicitudes vigentes para los filtros aplicados.", c.parametros.get("totales"));
    }

    @Test
    void todosLosEstadosTienenEtiqueta() {
        for (SolicitudPagoEstado e : SolicitudPagoEstado.values()) {
            assertTrue(ReporteSolicitudesPagoService.ESTADO_LABELS.containsKey(e), "Falta la etiqueta de " + e);
        }
    }

    /**
     * La plantilla se compila en runtime y un texto sin alto suficiente sale vacío sin error, así que se asevera
     * sobre el texto del fill y no solo sobre el peso del PDF.
     */
    @Test
    void plantillaImprimeEncabezadoTotalesYFilas() throws Exception {
        SolicitudPago larga = solicitud("SP-0001234", 123456789d, gs, SolicitudPagoEstado.SOLICITADO);
        larga.setProveedor(proveedor(PROVEEDOR_LARGO));
        List<SolicitudPago> lista = Arrays.asList(larga,
                solicitud("SP-0001235", 9876.54d, us, SolicitudPagoEstado.CANCELADO));
        ReporteSolicitudesPagoService.Contenido c = service.armar(lista, null, SolicitudPagoEstado.SOLICITADO, null,
                "2026-11-01", null, Arrays.asList(gs, us), usuario());

        JasperPrint print = service.llenar(c);
        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertTrue(pdf != null && pdf.length > 0);

        String textos = textos(print);
        for (String esperado : new String[]{
                "Solicitudes de pago", "Filtros aplicados", "| Solicitado |", "| GUARANI (Gs.), DOLAR (US$) |",
                "01/11/2026", "Sin límite", "Resumen", "| JUAN PEREZ |", "Generado el:", "| 2 |",
                "Total solicitado:", "| GUARANI: Gs. 123.456.789 |",
                "N.º", "Proveedor", "Fecha", "Monto", "Estado",
                "| SP-0001234 |", "| Gs. 123.456.789 |", "| US$ 9.876,54 |", "| Cancelada |", "10/11/26 09:15",
                "Página 1 de"}) {
            assertTrue(textos.contains(esperado), "Falta '" + esperado + "' en el reporte. Textos: " + textos);
        }
        assertFalse(textos.contains("DOLAR: US$"), "la cancelada no suma. Textos: " + textos);
        assertFalse(textos.toLowerCase().contains("sin canceladas"), "el rótulo ya no dice «sin canceladas». Textos: " + textos);
        assertTrue(textos.contains("| " + PROVEEDOR_LARGO + " |"), "El proveedor salió cortado. Textos: " + textos);
    }

    /** Con logo la plantilla lo dibuja; sin logo (servidor sin logo.png) sale igual, como en el resto de los tests. */
    @Test
    void plantillaImprimeElLogoCuandoHay() throws Exception {
        // Tres monedas: el total vive dentro del cuadro Resumen y tiene que crecer, no cortarse.
        ReporteSolicitudesPagoService.Contenido c = service.armar(Arrays.asList(
                        solicitud("SP-1", 123456789d, gs, SolicitudPagoEstado.SOLICITADO),
                        solicitud("SP-2", 1250.5d, us, SolicitudPagoEstado.SOLICITADO),
                        solicitud("SP-3", 80.25d, moneda(3L, "REAL", "R$", 2), SolicitudPagoEstado.SOLICITADO)),
                null, null, null, null, null, Collections.emptyList(), usuario());
        assertNull(c.parametros.get("logo"), "sin ImageService no hay logo");
        c.parametros.put("logo", new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB));

        JasperPrint print = service.llenar(c);
        assertTrue(imagenes(print.getPages().get(0).getElements()) > 0, "el logo no se dibujó");
        assertTrue(JasperExportManager.exportReportToPdf(print).length > 0);
        String textos = textos(print);
        assertTrue(textos.contains("| GUARANI: Gs. 123.456.789\nDOLAR: US$ 1.250,50\nREAL: R$ 80,25 |"),
                "el total por moneda salió cortado. Textos: " + textos);
    }

    private static int imagenes(List<?> elementos) {
        int n = 0;
        for (Object o : elementos) {
            if (o instanceof JRPrintImage) n++;
            else if (o instanceof JRPrintFrame) n += imagenes(((JRPrintFrame) o).getElements());
        }
        return n;
    }

    @Test
    void plantillaSinSolicitudesIgualImprimeElEncabezado() throws Exception {
        String textos = textos(service.llenar(service.armar(Collections.emptyList(), null, null, null,
                null, null, Collections.emptyList(), usuario())));
        assertTrue(textos.contains("Sin solicitudes vigentes para los filtros aplicados."), textos);
        assertTrue(textos.contains("Filtros aplicados"), textos);
    }

    private static String textos(JasperPrint print) {
        StringBuilder sb = new StringBuilder();
        for (JRPrintPage page : print.getPages()) juntar(page.getElements(), sb);
        return sb.toString();
    }

    /** Los frames anidan sus elementos; si Jasper truncó un texto, cuenta solo hasta el índice de corte. */
    private static void juntar(List<?> elementos, StringBuilder sb) {
        for (Object o : elementos) {
            if (o instanceof JRPrintText) {
                JRPrintText t = (JRPrintText) o;
                String texto = t.getFullText() != null ? t.getFullText() : "";
                Integer corte = t.getTextTruncateIndex();
                sb.append("| ").append(corte != null && corte < texto.length() ? texto.substring(0, corte) : texto).append(" ");
            } else if (o instanceof JRPrintFrame) {
                juntar(((JRPrintFrame) o).getElements(), sb);
            }
        }
    }

    private SolicitudPago solicitud(String numero, Double monto, Moneda moneda, SolicitudPagoEstado estado) {
        SolicitudPago s = new SolicitudPago();
        s.setNumeroSolicitud(numero);
        s.setMontoTotal(monto);
        s.setMoneda(moneda);
        s.setEstado(estado);
        s.setFechaSolicitud(FECHA);
        s.setProveedor(proveedor("acme sa"));
        return s;
    }

    private static Proveedor proveedor(String nombre) {
        Persona p = new Persona();
        p.setNombre(nombre);
        Proveedor pr = new Proveedor();
        pr.setPersona(p);
        return pr;
    }

    private static Usuario usuario() {
        Persona p = new Persona();
        p.setNombre("JUAN PEREZ");
        Usuario u = new Usuario();
        u.setPersona(p);
        return u;
    }

    private static Moneda moneda(Long id, String denominacion, String simbolo, int decimales) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setSimbolo(simbolo);
        m.setDecimales(decimales);
        return m;
    }
}
