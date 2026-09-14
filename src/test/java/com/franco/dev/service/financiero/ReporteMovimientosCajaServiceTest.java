package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reporte de movimientos del dashboard de caja mayor: armado del contenido (etiquetas, totales)
 * y la plantilla movimientos-caja.jrxml, que se compila recién en runtime.
 */
class ReporteMovimientosCajaServiceTest {

    private final ReporteMovimientosCajaService service =
            new ReporteMovimientosCajaService(null, null, null, null, null, null);

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 9, 10, 14, 30);
    private static final String NOMBRE_LARGO = "MAURO ROLANDO RIVAS FERNÁNDEZ";

    @Test
    void cajaTotalizaPorMonedaSinContarAnuladosNiSusContraMovimientos() {
        Moneda gs = moneda(1L, "GUARANIES", "Gs.", 0);
        Moneda us = moneda(2L, "DOLARES", "US$", 2);
        List<MovimientoCajaVirtual> movs = Arrays.asList(
                movCaja(CajaVirtualTipoMovimiento.INGRESO, 100000d, gs, OrigenMovimientoTipo.RETIRO_CAJA, true),
                movCaja(CajaVirtualTipoMovimiento.EGRESO, 30000d, gs, OrigenMovimientoTipo.GASTO, true),
                // anulado + su contra-movimiento: ninguno de los dos suma
                movCaja(CajaVirtualTipoMovimiento.EGRESO, 5000d, gs, OrigenMovimientoTipo.MANUAL, false),
                movCaja(CajaVirtualTipoMovimiento.AJUSTE, 5000d, gs, OrigenMovimientoTipo.ANULACION, true),
                // AJUSTE manual conserva su signo
                movCaja(CajaVirtualTipoMovimiento.AJUSTE, -2000d, gs, OrigenMovimientoTipo.MANUAL, true),
                movCaja(CajaVirtualTipoMovimiento.INGRESO, 12.5d, us, null, true));

        ReporteMovimientosCajaService.Contenido c = service.armarCaja(caja(), movs,
                "2026-09-01 00:00", "2026-09-14 00:00", null, null, false, usuario());

        assertEquals(6, c.filas.size());
        assertEquals("Retiro de PDV", c.filas.get(0).getTipo());
        assertEquals("Egreso", c.filas.get(2).getTipo(), "MANUAL cae a la etiqueta del tipo");
        assertEquals("Ingreso", c.filas.get(5).getTipo(), "sin origen cae a la etiqueta del tipo");
        assertTrue(c.filas.get(2).getAnulado());
        assertEquals("100.000 Gs.", c.filas.get(0).getMonto());
        assertEquals("12,50 US$", c.filas.get(5).getMonto());

        String totales = (String) c.parametros.get("totales");
        assertTrue(totales.contains("Ingresos: 100.000 Gs. - Egresos: 32.000 Gs. - Total: 68.000 Gs."), totales);
        assertTrue(totales.contains("Ingresos: 12,50 US$ - Egresos: 0,00 US$ - Total: 12,50 US$"), totales);
        assertEquals("1.000.000 Gs.", c.filas.get(0).getSaldo(), "el saldo lleva el símbolo: sin filtro de moneda se mezclan");
        assertEquals("Caja Mayor", c.parametros.get("fuente"));
        assertEquals("Todas", c.parametros.get("monedaFiltro"));
        assertEquals("01/09/2026", c.parametros.get("desdeFiltro"));
        assertEquals("14/09/2026", c.parametros.get("hastaFiltro"));
        assertEquals("6", c.parametros.get("cantidad"));
    }

    @Test
    void bancoTotalizaConElSignoDelTipo() {
        Moneda gs = moneda(1L, "GUARANIES", "Gs.", 0);
        CuentaBancaria cuenta = new CuentaBancaria();
        cuenta.setNumero("123-456");
        Banco banco = new Banco();
        banco.setNombre("ITAU");
        cuenta.setBanco(banco);
        cuenta.setMoneda(gs);
        List<MovimientoBancario> movs = Arrays.asList(
                movBanco(MovimientoBancarioTipo.ENTRADA_MANUAL, "5000", null, false),
                movBanco(MovimientoBancarioTipo.SALIDA_MANUAL, "1000", null, false),
                movBanco(MovimientoBancarioTipo.ENTRADA_MANUAL, "700", null, true),
                movBanco(MovimientoBancarioTipo.AJUSTE_NEGATIVO, "700", OrigenMovimientoTipo.ANULACION.name(), false));

        ReporteMovimientosCajaService.Contenido c = service.armarBanco(caja(), cuenta, movs,
                null, null, "SALIDA_MANUAL", true, usuario());

        assertEquals("ITAU - 123-456", c.parametros.get("fuente"));
        assertEquals("Salida", c.parametros.get("tipoFiltro"));
        assertEquals("Sin límite", c.parametros.get("desdeFiltro"));
        assertEquals("No incluidos", c.parametros.get("anuladosFiltro"));
        assertEquals("Ajuste −", c.filas.get(3).getTipo());
        assertTrue(((String) c.parametros.get("totales")).contains("Ingresos: 5.000 Gs. - Egresos: 1.000 Gs. - Total: 4.000 Gs."));
    }

    @Test
    void finDelRangoIncluyeElDiaCompleto() {
        assertEquals(LocalDateTime.of(2026, 9, 14, 23, 59, 59), MovimientoCajaVirtualService.finRango("2026-09-14 00:00"));
        assertNull(MovimientoCajaVirtualService.finRango(""));
        assertNull(MovimientoCajaVirtualService.finRango(null));
    }

    @Test
    void tipoBancarioInvalidoSeRechaza() {
        assertNull(MovimientoBancarioService.tipoValido(null));
        assertEquals("ENTRADA_MANUAL", MovimientoBancarioService.tipoValido("ENTRADA_MANUAL"));
        assertThrows(graphql.GraphQLException.class, () -> MovimientoBancarioService.tipoValido("CUALQUIERA"));
    }

    /**
     * La plantilla se compila en runtime y un texto sin alto suficiente sale vacío sin error, así
     * que se asevera sobre el texto del fill y no solo sobre el peso del PDF.
     */
    @Test
    void plantillaImprimeEncabezadoResumenYFilas() throws Exception {
        Moneda gs = moneda(1L, "GUARANIES", "Gs.", 0);
        // El segundo movimiento lleva un importe grande: las columnas Monto/Saldo son angostas y
        // Jasper corta sin avisar si el número no entra.
        List<MovimientoCajaVirtual> movs = Arrays.asList(
                movCaja(CajaVirtualTipoMovimiento.INGRESO, 250000d, gs, OrigenMovimientoTipo.RETIRO_CAJA, true),
                movCaja(CajaVirtualTipoMovimiento.AJUSTE, -12345678d, gs, OrigenMovimientoTipo.MANUAL, true));
        ReporteMovimientosCajaService.Contenido c = service.armarCaja(caja(), movs,
                "2026-09-01 00:00", null, CajaVirtualTipoMovimiento.INGRESO, gs, false, usuario());

        JasperPrint print = service.llenar(c);
        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertTrue(pdf != null && pdf.length > 0);

        String textos = textos(print);
        for (String esperado : new String[]{
                "Movimientos de CAJA MAYOR CENTRAL", "Fuente: Caja Mayor", "Filtros aplicados", "01/09/2026",
                "Sin límite", "Ingreso", "GUARANIES (Gs.)", "Incluidos (tachados)", "Resumen", "| JUAN PEREZ ",
                "Generado el:", "Total por moneda (sin anulados)", "Total: -12.095.678 Gs.", "| -12.345.678 Gs. ",
                "Fecha", "Responsable", "Tipo", "Descripción", "Monto", "Saldo",
                "10/09/26 14:30", "Retiro de PDV", "DESCRIPCION DE PRUEBA", "250.000 Gs.", "1.000.000 Gs.",
                "Página 1 de"}) {
            assertTrue(textos.contains(esperado), "Falta '" + esperado + "' en el reporte. Textos: " + textos);
        }
        // Nombre largo: tiene que salir completo en la columna Responsable, no cortado a lo que entra
        // en una línea (Jasper recorta sin avisar).
        assertTrue(textos.contains("| " + NOMBRE_LARGO + " |"), "El responsable salió cortado. Textos: " + textos);
    }

    @Test
    void plantillaSinMovimientosIgualImprimeElEncabezado() throws Exception {
        ReporteMovimientosCajaService.Contenido c = service.armarCaja(caja(), Collections.emptyList(),
                null, null, null, null, true, usuario());
        String textos = textos(service.llenar(c));
        assertTrue(textos.contains("Sin movimientos vigentes para los filtros aplicados."), textos);
        assertTrue(textos.contains("Filtros aplicados"), textos);
    }

    private static String textos(JasperPrint print) {
        StringBuilder sb = new StringBuilder();
        for (JRPrintPage page : print.getPages()) {
            juntar(page.getElements(), sb);
        }
        return sb.toString();
    }

    /** Los frames anidan sus elementos: hay que recorrerlos para ver el texto de adentro. */
    private static void juntar(List<?> elementos, StringBuilder sb) {
        for (Object o : elementos) {
            if (o instanceof JRPrintText) {
                // Texto que efectivamente se imprime: si Jasper lo truncó, solo hasta el índice de corte.
                JRPrintText t = (JRPrintText) o;
                String texto = t.getFullText() != null ? t.getFullText() : "";
                Integer corte = t.getTextTruncateIndex();
                sb.append("| ").append(corte != null && corte < texto.length() ? texto.substring(0, corte) : texto).append(" ");
            } else if (o instanceof net.sf.jasperreports.engine.JRPrintFrame) {
                juntar(((net.sf.jasperreports.engine.JRPrintFrame) o).getElements(), sb);
            }
        }
    }

    private static CajaVirtual caja() {
        CajaVirtual c = new CajaVirtual();
        c.setId(7L);
        c.setNombre("CAJA MAYOR CENTRAL");
        return c;
    }

    /** Quien genera el reporte. Distinto del responsable de los movimientos, para aseverar cada columna por separado. */
    private static Usuario usuario() {
        return usuario("JUAN PEREZ");
    }

    private static Usuario usuario(String nombre) {
        Persona p = new Persona();
        p.setNombre(nombre);
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

    private static MovimientoCajaVirtual movCaja(CajaVirtualTipoMovimiento tipo, Double cantidad, Moneda moneda,
                                                 OrigenMovimientoTipo origen, boolean activo) {
        MovimientoCajaVirtual m = new MovimientoCajaVirtual();
        m.setTipoMovimiento(tipo);
        m.setCantidad(cantidad);
        m.setMoneda(moneda);
        m.setOrigenTipo(origen);
        m.setActivo(activo);
        m.setSaldoPosterior(1000000d);
        m.setDescripcion("DESCRIPCION DE PRUEBA");
        m.setCreadoEn(FECHA);
        m.setUsuario(usuario(NOMBRE_LARGO));
        return m;
    }

    private static MovimientoBancario movBanco(MovimientoBancarioTipo tipo, String monto, String origen, boolean anulado) {
        MovimientoBancario m = new MovimientoBancario();
        m.setTipoMovimiento(tipo);
        m.setMonto(new BigDecimal(monto));
        m.setOrigenTipo(origen);
        m.setAnulado(anulado);
        m.setSaldoPosterior(new BigDecimal("10000"));
        m.setCreadoEn(FECHA);
        return m;
    }
}
