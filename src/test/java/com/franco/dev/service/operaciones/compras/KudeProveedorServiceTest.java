package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arma el KuDE de una factura real de proveedor. Necesita la respuesta de SIFEN en un archivo:
 * {@code -Dkude.xml=/ruta/respuesta.xml}; deja el PDF al lado ({@code respuesta.xml.pdf}).
 */
class KudeProveedorServiceTest {

    @Test
    @EnabledIfSystemProperty(named = "kude.xml", matches = ".+")
    void armaElPdfDeUnaFacturaReal() throws Exception {
        String ruta = System.getProperty("kude.xml");
        DocumentoRecibido doc = new DocumentoRecibido();
        doc.setXml(new String(Files.readAllBytes(Paths.get(ruta)), StandardCharsets.UTF_8));
        byte[] pdf = new KudeProveedorService().pdf(doc);
        assertTrue(pdf.length > 1000);
        Files.write(Paths.get(ruta + ".pdf"), pdf);
    }

    @Test
    void losParametrosSalenDelXml() {
        FacturaSifenXml.Cabecera c = new FacturaSifenXml.Cabecera();
        c.rucEmisor = "80053406"; c.dvEmisor = "9"; c.razonSocialEmisor = "GENFI SRL";
        c.establecimiento = "001"; c.puntoExpedicion = "001"; c.numero = "0055357";
        c.condicionOperacion = 2; c.plazoCredito = 30; c.moneda = "PYG"; c.totalGeneral = 1000.0;
        java.util.Map<String, Object> p = KudeProveedorService.parametros(c);
        assertTrue("80053406-9".equals(p.get("rucEmisor")));
        assertTrue("001-001-0055357".equals(p.get("numeroFactura")));
        assertTrue(Boolean.TRUE.equals(p.get("credito")));
        assertTrue("30 días".equals(p.get("plazo")));
        assertTrue("".equals(p.get("logo")));
    }
}
