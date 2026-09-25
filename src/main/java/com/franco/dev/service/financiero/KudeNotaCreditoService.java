package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.NotaCredito;
import com.franco.dev.domain.financiero.NotaCreditoItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.service.sifen.util.SifenTimbradoHelper;
import com.franco.dev.utilitarios.DateUtils;
import com.franco.dev.utilitarios.print.QRCodeImageGenerator;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KuDE de la nota de crédito, en PDF A4.
 *
 * Mismo criterio que el de la remisión: la plantilla se compila en runtime, así que el único gate
 * real es el test que la llena y lee el texto renderizado. La plantilla portada de frc-efact no
 * declaraba ninguna fuente; acá las 40 llevan `SansSerif` explícito (regla del repo).
 */
@Slf4j
@Service
public class KudeNotaCreditoService {

    public static final String PLANTILLA = "reports/nota-credito-kude.jrxml";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String generarPdfBase64(NotaCredito nota, List<NotaCreditoItem> items,
                                   TimbradoDetalle timbradoDetalle, DocumentoElectronico de,
                                   String cdcFactura) throws JRException {
        JasperPrint impreso = llenar(nota, items, timbradoDetalle, de, cdcFactura);
        return Base64.getEncoder().encodeToString(JasperExportManager.exportReportToPdf(impreso));
    }

    /** Ver {@code KudeNotaRemisionService.llenar}: el test mira el texto, no el peso del PDF. */
    public JasperPrint llenar(NotaCredito nota, List<NotaCreditoItem> items,
                              TimbradoDetalle timbradoDetalle, DocumentoElectronico de,
                              String cdcFactura) throws JRException {
        JasperReport reporte = compilar();

        List<ItemKude> filas = new ArrayList<>();
        for (NotaCreditoItem item : items) {
            filas.add(new ItemKude(item));
        }
        JRBeanCollectionDataSource datos = new JRBeanCollectionDataSource(filas);

        String qrImagePath = "";
        Map<String, Object> parametros = parametros(nota, timbradoDetalle, de, cdcFactura);
        try {
            qrImagePath = archivoQr(de);
            parametros.put("qrImagePath", qrImagePath);
            return JasperFillManager.fillReport(reporte, parametros, datos);
        } finally {
            borrarTemporal(qrImagePath);
        }
    }

    JasperReport compilar() throws JRException {
        try (InputStream jrxml = new ClassPathResource(PLANTILLA).getInputStream()) {
            return JasperCompileManager.compileReport(jrxml);
        } catch (java.io.IOException e) {
            throw new JRException("No se pudo leer la plantilla " + PLANTILLA, e);
        }
    }

    public Map<String, Object> parametros(NotaCredito nota, TimbradoDetalle timbradoDetalle,
                                          DocumentoElectronico de, String cdcFactura) {
        Map<String, Object> p = new HashMap<>();
        p.put("logo", "");
        p.put("qrImagePath", "");

        if (timbradoDetalle != null && timbradoDetalle.getTimbrado() != null) {
            p.put("razonSocial", texto(timbradoDetalle.getTimbrado().getRazonSocial()));
            p.put("rucEmisor", texto(timbradoDetalle.getTimbrado().getRuc()));
            p.put("numeroTimbrado", texto(timbradoDetalle.getTimbrado().getNumero()));
            p.put("emailEmisor", texto(timbradoDetalle.getTimbrado().getEmail()));
            p.put("actividadEconomica", texto(timbradoDetalle.getTimbrado().getDescActividadEconomicaPrincipal()));
            p.put("fechaInicioVigencia", timbradoDetalle.getTimbrado().getFechaInicio() != null
                    ? DateUtils.toString(timbradoDetalle.getTimbrado().getFechaInicio()) : "");
            p.put("direccionEmisor", direccionEmisor(timbradoDetalle));
            p.put("telefonoEmisor", texto(SifenTimbradoHelper.telefonoEmisor(timbradoDetalle)));
        }

        p.put("numeroNotaCredito", numeroFormateado(nota, timbradoDetalle));
        p.put("fechaEmision", nota.getFecha() != null ? nota.getFecha().toLocalDate().format(FECHA) : "");
        p.put("motivoEmision", nota.getMotivoEmision() != null ? legible(nota.getMotivoEmision().name()) : "");
        p.put("descripcionMotivo", texto(nota.getDescripcionMotivo()));

        p.put("nombreCliente", texto(nota.getNombre()));
        p.put("rucCliente", texto(nota.getRuc()));
        p.put("direccionCliente", texto(nota.getDireccion()));

        boolean extranjera = nota.getMonedaExtranjera() != null
                && !nota.getMonedaExtranjera().trim().isEmpty()
                && !"PYG".equalsIgnoreCase(nota.getMonedaExtranjera().trim());
        p.put("tieneMonedaExtranjera", extranjera);
        p.put("moneda", extranjera ? nota.getMonedaExtranjera().trim().toUpperCase() : "GS");
        p.put("tipoCambio", extranjera && nota.getTipoCambio() != null
                ? nota.getTipoCambio().toPlainString() : "");

        // La plantilla declara estos como Double: pasarlos como String revienta el fill con un
        // ClassCastException que no dice cual es el parametro.
        p.put("subtotalExentas", numero(nota.getTotalParcial0()));
        p.put("subtotal5", numero(nota.getTotalParcial5()));
        p.put("subtotal10", numero(nota.getTotalParcial10()));
        p.put("totalIva5", numero(nota.getIvaParcial5()));
        p.put("totalIva10", numero(nota.getIvaParcial10()));
        p.put("totalIva", numero(suma(nota.getIvaParcial5(), nota.getIvaParcial10())));
        p.put("totalOperacion", numero(nota.getTotalFinal()));
        p.put("totalFinal", numero(nota.getTotalFinal()));
        p.put("totalEnGuarani", numero(totalEnGuaranies(nota)));

        p.put("cdc", de != null ? texto(de.getCdc()) : "");
        // Mismo bloque de consulta que el KuDE de la nota de remisión: la plantilla declaraba el
        // parámetro pero nadie lo llenaba.
        p.put("urlValidacion", "https://ekuatia.set.gov.py/consultas/");
        // El CDC de la factura es lo que ata la nota a su documento: en el KuDE también.
        p.put("cdcFacturaRelacionada", texto(cdcFactura));
        return p;
    }

    private String archivoQr(DocumentoElectronico de) {
        if (de == null || de.getUrlQr() == null || de.getUrlQr().trim().isEmpty()) {
            return "";
        }
        try {
            BufferedImage qr = QRCodeImageGenerator.generateQRCodeImage(de.getUrlQr(), 200, 200);
            File temporal = File.createTempFile("qr_nota_credito_", ".png");
            ImageIO.write(qr, "PNG", temporal);
            return temporal.getAbsolutePath();
        } catch (Exception e) {
            log.warn("No se pudo generar el QR de la nota de crédito: {}", e.getMessage());
            return "";
        }
    }

    private static void borrarTemporal(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            File archivo = new File(path);
            if (archivo.exists() && !archivo.delete()) {
                log.debug("No se pudo borrar el QR temporal {}", path);
            }
        } catch (Exception e) {
            log.debug("No se pudo borrar el QR temporal: {}", e.getMessage());
        }
    }

    private static String numeroFormateado(NotaCredito nota, TimbradoDetalle timbradoDetalle) {
        if (nota.getNumeroNotaCredito() == null) return "";
        String puntoExpedicion = "001";
        if (timbradoDetalle != null && timbradoDetalle.getPuntoExpedicion() != null) {
            try {
                puntoExpedicion = String.format("%03d", Integer.parseInt(timbradoDetalle.getPuntoExpedicion().trim()));
            } catch (NumberFormatException ignored) {
                // se queda el valor por defecto
            }
        }
        return "001-" + puntoExpedicion + "-" + String.format("%07d", nota.getNumeroNotaCredito());
    }

    private static String direccionEmisor(TimbradoDetalle detalle) {
        StringBuilder sb = new StringBuilder();
        if (detalle.getDireccion() != null && !detalle.getDireccion().isEmpty()) sb.append(detalle.getDireccion());
        if (detalle.getCiudad() != null && !detalle.getCiudad().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(detalle.getCiudad());
        }
        if (detalle.getDepartamento() != null && !detalle.getDepartamento().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(detalle.getDepartamento());
        }
        return sb.toString();
    }

    private static BigDecimal suma(BigDecimal uno, BigDecimal dos) {
        BigDecimal a = uno != null ? uno : BigDecimal.ZERO;
        BigDecimal b = dos != null ? dos : BigDecimal.ZERO;
        return a.add(b);
    }

    private static Double numero(BigDecimal valor) {
        return valor != null ? valor.doubleValue() : 0d;
    }

    /** En moneda extranjera el KuDE muestra además el equivalente en guaraníes. */
    private static BigDecimal totalEnGuaranies(NotaCredito nota) {
        BigDecimal total = nota.getTotalFinal() != null ? nota.getTotalFinal() : BigDecimal.ZERO;
        return total;   // los totales de la nota ya estan en guaranies, como en factura_legal
    }

    private static String texto(String valor) {
        return valor != null ? valor : "";
    }

    /** DEVOLUCION_Y_AJUSTES_DE_PRECIOS -> "Devolucion y ajustes de precios". */
    private static String legible(String nombreEnum) {
        String texto = nombreEnum.replace('_', ' ').toLowerCase();
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }

    /** Fila de la tabla; los nombres son los que espera el .jrxml. */
    public static class ItemKude {
        private final String codigo;
        private final String descripcion;
        private final Double cantidad;
        private final Double precioUnitario;
        private final Double montoExento;
        private final Double montoGravado5;
        private final Double montoGravado10;

        public ItemKude(NotaCreditoItem item) {
            this.codigo = item.getFacturaLegalItemId() != null
                    ? String.valueOf(item.getFacturaLegalItemId()) : "";
            this.descripcion = item.getDescripcion() != null ? item.getDescripcion() : "";
            this.cantidad = item.getCantidad() != null ? item.getCantidad().doubleValue() : 0d;
            this.precioUnitario = item.getPrecioUnitario() != null
                    ? item.getPrecioUnitario().doubleValue() : 0d;

            double total = item.getTotal() != null ? item.getTotal().doubleValue() : 0d;
            int iva = item.getIva() != null ? item.getIva() : 0;
            // Cada ítem cae en una sola columna, la de su tasa: es como lo lee el contador.
            this.montoExento = iva == 0 ? total : 0d;
            this.montoGravado5 = iva == 5 ? total : 0d;
            this.montoGravado10 = iva == 10 ? total : 0d;
        }

        public String getCodigo() { return codigo; }
        public String getDescripcion() { return descripcion; }
        public Double getCantidad() { return cantidad; }
        public Double getPrecioUnitario() { return precioUnitario; }
        public Double getMontoExento() { return montoExento; }
        public Double getMontoGravado5() { return montoGravado5; }
        public Double getMontoGravado10() { return montoGravado10; }
    }
}
