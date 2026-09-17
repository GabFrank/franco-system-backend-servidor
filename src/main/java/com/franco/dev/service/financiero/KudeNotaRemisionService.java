package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.NotaRemisionItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KuDE (representación gráfica) de la nota de remisión, en PDF A4.
 *
 * La plantilla se compila **en runtime**, así que un error en el `.jrxml` no lo ve ni el build ni el
 * CI: revienta al generar el PDF en producción. Por eso `NotaRemisionKudeJrxmlTest` la compila y la
 * llena con datos dummy, y por eso la plantilla usa solo `SansSerif` (fuente lógica de Java, siempre
 * disponible; la referencia traía un `Monospaced` que acá se reemplazó).
 */
@Slf4j
@Service
public class KudeNotaRemisionService {

    public static final String PLANTILLA = "reports/nota-remision-kude.jrxml";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** PDF en base64, listo para el visor de impresión del desktop. */
    public String generarPdfBase64(NotaRemision nota, List<NotaRemisionItem> items,
                                   TimbradoDetalle timbradoDetalle, DocumentoElectronico de) throws JRException {
        JasperPrint impreso = llenar(nota, items, timbradoDetalle, de);
        return Base64.getEncoder().encodeToString(JasperExportManager.exportReportToPdf(impreso));
    }

    /**
     * Llena la plantilla y devuelve el documento antes de exportarlo. Existe para que el test pueda
     * mirar el TEXTO renderizado: un campo con alto insuficiente sale vacío sin que Jasper falle, y
     * el PDF igual pesa. Verificar el peso no alcanza.
     */
    public JasperPrint llenar(NotaRemision nota, List<NotaRemisionItem> items,
                              TimbradoDetalle timbradoDetalle, DocumentoElectronico de) throws JRException {
        JasperReport reporte = compilar();
        List<ItemKude> filas = new ArrayList<>();
        for (NotaRemisionItem item : items) {
            filas.add(new ItemKude(item));
        }
        JRBeanCollectionDataSource datos = new JRBeanCollectionDataSource(filas);

        String qrImagePath = "";
        Map<String, Object> parametros = parametros(nota, timbradoDetalle, de);
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

    public Map<String, Object> parametros(NotaRemision nota, TimbradoDetalle timbradoDetalle, DocumentoElectronico de) {
        Map<String, Object> p = new HashMap<>();
        p.put("logo", "");
        p.put("qrImagePath", "");
        p.put("urlValidacion", "https://ekuatia.set.gov.py/consultas/");

        if (timbradoDetalle != null && timbradoDetalle.getTimbrado() != null) {
            p.put("razonSocial", texto(timbradoDetalle.getTimbrado().getRazonSocial()));
            p.put("rucEmisor", texto(timbradoDetalle.getTimbrado().getRuc()));
            p.put("numeroTimbrado", texto(timbradoDetalle.getTimbrado().getNumero()));
            p.put("emailEmisor", texto(timbradoDetalle.getTimbrado().getEmail()));
            p.put("actividadEconomica", texto(timbradoDetalle.getTimbrado().getDescActividadEconomicaPrincipal()));
            p.put("fechaInicioVigencia", timbradoDetalle.getTimbrado().getFechaInicio() != null
                    ? DateUtils.toString(timbradoDetalle.getTimbrado().getFechaInicio()) : "");
            p.put("direccionEmisor", direccionEmisor(timbradoDetalle));
            p.put("telefonoEmisor", texto(timbradoDetalle.getTelefono()));
        }

        p.put("numeroNotaRemision", numeroFormateado(nota, timbradoDetalle));
        p.put("fechaEmision", nota.getFecha() != null ? nota.getFecha().toLocalDate().format(FECHA) : "");
        p.put("motivoEmision", nota.getMotivoEmision() != null ? legible(nota.getMotivoEmision().name()) : "");
        p.put("kmEstimado", nota.getKmEstimado() != null ? String.valueOf(nota.getKmEstimado()) : "");
        p.put("fechaInicioTraslado", nota.getFechaInicioTraslado() != null
                ? nota.getFechaInicioTraslado().format(FECHA) : "");
        p.put("fechaFinTraslado", nota.getFechaFinTraslado() != null
                ? nota.getFechaFinTraslado().format(FECHA) : "");
        p.put("fechaEstimadaFactura", nota.getFechaEstimadaFactura() != null
                ? nota.getFechaEstimadaFactura().format(FECHA) : "");

        p.put("nombreDestinatario", texto(nota.getReceptorNombre()));
        p.put("rucDestinatario", texto(nota.getReceptorRuc()));
        p.put("direccionPartida", texto(nota.getSalidaDireccion()));
        p.put("ciudadPartida", texto(nota.getSalidaCiudad()));
        p.put("departamentoPartida", texto(nota.getSalidaDepartamento()));
        p.put("direccionLlegada", texto(nota.getEntregaDireccion()));
        p.put("ciudadLlegada", texto(nota.getEntregaCiudad()));
        p.put("departamentoLlegada", texto(nota.getEntregaDepartamento()));

        p.put("tipoTransporte", nota.getTipoTransporte() != null ? legible(nota.getTipoTransporte().name()) : "");
        p.put("modalidadTransporte", nota.getModalidadTransporte() != null
                ? legible(nota.getModalidadTransporte().name()) : "");
        p.put("transportistaNombre", texto(nota.getTransportistaNombre()));
        p.put("transportistaRuc", texto(nota.getTransportistaRuc()));
        // El KuDE muestra la marca completa; al XML va abreviada a 10 caracteres (regla de SIFEN).
        p.put("vehiculoMarca", texto(nota.getVehiculoMarca()));
        p.put("vehiculoMatricula", texto(nota.getVehiculoMatricula()));
        p.put("conductorNombre", texto(nota.getChoferNombre()));
        p.put("conductorDoc", texto(nota.getChoferDocumento()));
        p.put("numeroFacturaAsociada", nota.getFacturaLegalId() != null
                ? String.valueOf(nota.getFacturaLegalId()) : "");

        p.put("cdc", de != null ? texto(de.getCdc()) : "");
        return p;
    }

    private String archivoQr(DocumentoElectronico de) {
        if (de == null || de.getUrlQr() == null || de.getUrlQr().trim().isEmpty()) {
            return "";
        }
        try {
            BufferedImage qr = QRCodeImageGenerator.generateQRCodeImage(de.getUrlQr(), 200, 200);
            File temporal = File.createTempFile("qr_nota_remision_", ".png");
            ImageIO.write(qr, "PNG", temporal);
            return temporal.getAbsolutePath();
        } catch (Exception e) {
            log.warn("No se pudo generar el QR de la nota de remisión: {}", e.getMessage());
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

    /** est-pexp-0000001, el formato que usa SIFEN para el número del documento. */
    private static String numeroFormateado(NotaRemision nota, TimbradoDetalle timbradoDetalle) {
        if (nota.getNumeroNotaRemision() == null) return "";
        String establecimiento = "001";
        String puntoExpedicion = "001";
        if (timbradoDetalle != null && timbradoDetalle.getPuntoExpedicion() != null) {
            try {
                puntoExpedicion = String.format("%03d", Integer.parseInt(timbradoDetalle.getPuntoExpedicion().trim()));
            } catch (NumberFormatException ignored) {
                // se queda el valor por defecto
            }
        }
        return establecimiento + "-" + puntoExpedicion + "-" + String.format("%07d", nota.getNumeroNotaRemision());
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

    private static String texto(String valor) {
        return valor != null ? valor : "";
    }

    /** TRASLADO_ENTRE_LOCALES -> "Traslado entre locales". */
    private static String legible(String nombreEnum) {
        String texto = nombreEnum.replace('_', ' ').toLowerCase();
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }

    /** Fila de la tabla de ítems; los nombres son los que espera el .jrxml. */
    public static class ItemKude {
        private final String codigo;
        private final String descripcion;
        private final Double cantidad;
        private final String unidadMedida;

        public ItemKude(NotaRemisionItem item) {
            this.codigo = item.getCodigo() != null ? item.getCodigo() : "";
            this.descripcion = item.getDescripcion() != null ? item.getDescripcion() : "";
            this.cantidad = item.getCantidad() != null ? item.getCantidad().doubleValue() : 0d;
            this.unidadMedida = item.getUnidadMedida() != null ? item.getUnidadMedida() : "UNI";
        }

        public String getCodigo() { return codigo; }
        public String getDescripcion() { return descripcion; }
        public Double getCantidad() { return cantidad; }
        public String getUnidadMedida() { return unidadMedida; }
    }
}
