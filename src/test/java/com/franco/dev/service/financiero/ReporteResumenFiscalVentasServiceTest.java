package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.dto.ResumenFiscalVentas;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF del resumen fiscal: armado de las filas pre-formateadas y la plantilla
 * resumen-fiscal-ventas.jrxml, que se compila recien en runtime.
 */
class ReporteResumenFiscalVentasServiceTest {

    private final ReporteResumenFiscalVentasService service = new ReporteResumenFiscalVentasService(null, null);

    @Test
    void armaResumenYDetalleConTotal() {
        ResumenFiscalVentas r = ResumenFiscalVentasService.armar(2026, 7, "Todas", Arrays.asList(
                fila(1L, "SUC. CENTRAL", "001", "001", true, 16545, 99, 156442, 173081,
                        "628184870", "57107715", "52246581", "2487932", "0"),
                fila(3L, "SUC. ROTONDA", "002", "001", false, 10, 0, 1, 10,
                        "1100", "100", null, null, "500")));

        ReporteResumenFiscalVentasService.Contenido c =
                ReporteResumenFiscalVentasService.armar(r, r.getContribuyentes().get(0), "ADMIN", "30/09/2026 10:00");

        assertEquals("Gravadas 10%", c.resumen.get(0).getConcepto());
        assertEquals("571.078.155", c.resumen.get(0).getMonto());
        assertEquals("57.107.815", c.resumen.get(0).getIva());
        assertEquals("Total", c.resumen.get(3).getConcepto());
        assertTrue(c.resumen.get(3).getTotal());

        assertEquals(3, c.detalle.size(), "dos timbrados + la fila de total");
        assertEquals("SUC. CENTRAL / 18270044", c.detalle.get(0).getSucursal());
        assertEquals("001-001-0156442 al 001-001-0173081", c.detalle.get(0).getRango());
        assertEquals("16.545", c.detalle.get(0).getEmitidas());
        assertTrue(c.detalle.get(2).getTotal());
        assertEquals("16.555", c.detalle.get(2).getEmitidas());

        assertEquals("80099482-5", c.parametros.get("ruc"));
        assertEquals("Julio 2026", c.parametros.get("periodo"));
    }

    @Test
    void laPlantillaCompilaYGeneraUnPdfDeVariasPaginas() throws Exception {
        List<Object[]> filas = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            filas.add(fila((long) i, "SUC. NUEVA ESPERANZA SAN ANTONIO " + i, String.format("%03d", i), "001",
                    i % 2 == 0, 2000 + i, i % 5, 1000L * i, 1000L * i + 2000, "123456789", "11223344",
                    "4200000", "200000", "0"));
        }
        ResumenFiscalVentas r = ResumenFiscalVentasService.armar(2026, 7, "Todas", filas);
        ReporteResumenFiscalVentasService.Contenido c =
                ReporteResumenFiscalVentasService.armar(r, r.getContribuyentes().get(0), "ADMIN", "30/09/2026 10:00");

        byte[] pdf = service.exportarBytes(Collections.singletonList(c));

        assertTrue(pdf.length > 1000);
        assertEquals("%PDF", new String(pdf, 0, 4));
        assertTrue(service.llenar(c).getPages().size() >= 2, "41 filas de detalle no entran en una pagina");
        // Para revisarlo a ojo: target/resumen-fiscal-ventas-test.pdf
        Files.write(new File("target/resumen-fiscal-ventas-test.pdf").toPath(), pdf);
    }

    private static Object[] fila(Long sucId, String sucursal, String establecimiento, String punto,
                                 boolean electronico, long emitidas, long anuladas, long desde, long hasta,
                                 String t10, String i10, String t5, String i5, String t0) {
        return new Object[]{"80099482-5", "FRANCO AREVALOS S.A.", BigDecimal.valueOf(sucId), sucursal,
                establecimiento, punto, electronico ? "18270044" : "17599896", electronico,
                BigDecimal.valueOf(emitidas), BigDecimal.valueOf(anuladas), new BigDecimal(desde),
                new BigDecimal(hasta), dec(t10), dec(i10), dec(t5), dec(i5), dec(t0)};
    }

    private static BigDecimal dec(String v) {
        return v != null ? new BigDecimal(v) : null;
    }
}
