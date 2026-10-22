package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.utilitarios.print.QRCodeImageGenerator;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El KuDE de una factura de proveedor, armado con los datos del XML que devolvió SIFEN y con la misma plantilla de
 * nuestras facturas electrónicas ({@code factura-electronica-kude.jrxml}).
 *
 * No es el PDF que imprimió el proveedor —ese no lo guarda la SET, cada emisor diseña el suyo—, sino una
 * representación con todos los datos legales del documento: emisor, timbrado, receptor, renglones, IVA, totales,
 * CDC y QR. Sin logo: el de la plantilla es el nuestro.
 */
@Service
public class KudeProveedorService {

    private static final Logger log = LoggerFactory.getLogger(KudeProveedorService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public byte[] pdf(DocumentoRecibido doc) {
        if (doc.getXml() == null || doc.getXml().trim().isEmpty()) {
            throw new IllegalArgumentException("La factura no tiene el XML de SIFEN guardado.");
        }
        FacturaSifenXml.Cabecera c = FacturaSifenXml.leer(doc.getXml());
        File qr = null;
        try {
            JasperReport reporte;
            try (InputStream jrxml = new ClassPathResource("reports/factura-electronica-kude.jrxml").getInputStream()) {
                reporte = JasperCompileManager.compileReport(jrxml);
            }
            Map<String, Object> p = parametros(c);
            if (c.urlQr != null && !c.urlQr.trim().isEmpty()) {
                try {
                    BufferedImage imagen = QRCodeImageGenerator.generateQRCodeImage(c.urlQr.trim(), 200, 200);
                    qr = File.createTempFile("qr_proveedor_", ".png");
                    ImageIO.write(imagen, "PNG", qr);
                    p.put("qrImagePath", qr.getAbsolutePath());
                } catch (Exception e) {
                    log.warn("No se pudo generar el QR del KuDE de {}: {}", c.cdc, e.getMessage());
                }
            }
            JasperPrint impreso = JasperFillManager.fillReport(reporte, p, new JRBeanCollectionDataSource(items(c)));
            return JasperExportManager.exportReportToPdf(impreso);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo armar el PDF de la factura: " + e.getMessage(), e);
        } finally {
            if (qr != null && qr.exists() && !qr.delete()) qr.deleteOnExit();
        }
    }

    static Map<String, Object> parametros(FacturaSifenXml.Cabecera c) {
        Map<String, Object> p = new HashMap<>();
        p.put("logo", "");
        p.put("razonSocial", nz(c.razonSocialEmisor));
        p.put("rucEmisor", c.rucEmisor == null ? "" : c.rucEmisor + (c.dvEmisor != null ? "-" + c.dvEmisor : ""));
        p.put("numeroTimbrado", c.timbrado != null ? String.valueOf(c.timbrado) : "");
        p.put("fechaInicioVigencia", c.inicioTimbrado != null ? c.inicioTimbrado.format(FECHA) : "");
        p.put("direccionEmisor", unir(c.direccionEmisor, c.ciudadEmisor, c.departamentoEmisor));
        p.put("telefonoEmisor", nz(c.telefonoEmisor));
        p.put("emailEmisor", nz(c.emailEmisor));
        p.put("actividadEconomica", nz(c.actividadEconomica));
        p.put("numeroFactura", nz(c.establecimiento) + "-" + nz(c.puntoExpedicion) + "-" + nz(c.numero));
        p.put("cdc", nz(c.cdc));
        p.put("fechaEmision", c.fechaEmision != null ? c.fechaEmision.format(FECHA_HORA) : "");
        p.put("presupuesto", "");
        p.put("ordenAsociada", "");
        p.put("rucCliente", c.rucReceptor == null ? "" : c.rucReceptor + (c.dvReceptor != null ? "-" + c.dvReceptor : ""));
        p.put("nombreCliente", nz(c.razonSocialReceptor));
        p.put("fantasiaCliente", nz(c.razonSocialReceptor));
        p.put("direccionCliente", nz(c.direccionReceptor));
        p.put("ciudadCliente", "");
        p.put("departamentoCliente", "");
        p.put("telefonoCliente", nz(c.telefonoReceptor));
        p.put("emailCliente", nz(c.emailReceptor));
        boolean credito = c.condicionOperacion != null && c.condicionOperacion == 2;
        p.put("contado", !credito);
        p.put("credito", credito);
        p.put("cuotas", "");
        p.put("plazo", c.plazoCredito != null ? c.plazoCredito + " días" : "");
        String moneda = c.moneda != null ? c.moneda : "PYG";
        boolean extranjera = !"PYG".equalsIgnoreCase(moneda);
        p.put("moneda", moneda);
        p.put("tipoCambio", extranjera && c.cotizacion != null ? String.valueOf(c.cotizacion) : "");
        // Subtotales y totales tal como los informa SIFEN (gTotSub): netos de descuento, IVA incluido
        p.put("subtotalExentas", d(c.subtotalExenta));
        p.put("subtotal5", d(c.subtotalIva5));
        p.put("subtotal10", d(c.subtotalIva10));
        p.put("totalOperacion", d(c.totalGeneral));
        p.put("totalIva5", d(c.totalIva5));
        p.put("totalIva10", d(c.totalIva10));
        p.put("totalIva", c.totalIva != null ? c.totalIva : d(c.totalIva5) + d(c.totalIva10));
        p.put("totalFinal", d(c.totalGeneral));
        p.put("totalEnGuarani", extranjera && c.cotizacion != null ? d(c.totalGeneral) * c.cotizacion : d(c.totalGeneral));
        p.put("urlValidacion", "https://ekuatia.set.gov.py/consultas/");
        p.put("qrImagePath", "");
        return p;
    }

    static List<FacturaLegalGraphQL.FacturaItemDto> items(FacturaSifenXml.Cabecera c) {
        List<FacturaLegalGraphQL.FacturaItemDto> lista = new ArrayList<>();
        long id = 1;
        for (FacturaSifenXml.Renglon r : c.renglones) {
            FacturaLegalGraphQL.FacturaItemDto dto = new FacturaLegalGraphQL.FacturaItemDto();
            dto.setId(id++);
            dto.setCodigo(nz(r.codigoProveedor));
            dto.setDescripcion(nz(r.descripcion));
            dto.setDescripcionPresentacion(nz(r.unidadMedida));
            dto.setCantidad(r.cantidad != null ? r.cantidad.floatValue() : 0f);
            dto.setPrecioUnitario(d(r.precioUnitario));
            // Exenta (iAfecIVA 2 o 3) sin tasa: la columna de exentas
            dto.setIva(r.tasaIva != null && (r.afectacionIva == null || r.afectacionIva != 2) ? r.tasaIva : 0);
            dto.setTotal(r.total != null ? r.total : d(r.cantidad) * d(r.precioUnitario));
            lista.add(dto);
        }
        return lista;
    }

    private static String unir(String... partes) {
        StringBuilder b = new StringBuilder();
        for (String s : partes) {
            if (s == null || s.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(", ");
            b.append(s.trim());
        }
        return b.toString();
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private static double d(Double v) {
        return v != null ? v : 0.0;
    }
}
