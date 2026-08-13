package com.franco.dev.service.impresion;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionConfiguracion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.dto.EtiquetaDevolucionFilaDto;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.operaciones.DevolucionConfiguracionService;
import com.franco.dev.service.operaciones.DevolucionItemService;
import com.franco.dev.service.operaciones.DevolucionService;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.PresentacionUtils;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.barcode.QRCode;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.GraphQLException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.print.PrintService;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Etiquetas de separado (una por item). Se imprimen al marcar SEPARADO para
 * pegar en cada bulto. Contienen identificador, producto, cantidad, motivo y un
 * QR con el identificador (para reusar el escaneo de retiro).
 *
 * Dos salidas: ticket termico (ESC/POS) y PDF A4 (Jasper, 2 columnas cortables).
 *
 * Usado en:
 * - Desktop: Si
 * - Mobile: No
 */
@Service
public class EtiquetaDevolucionService {

    private static final String IMPRESORA_DEFAULT = "TICKET58";
    private static final int COLUMNAS_58 = 32;
    private static final int COLUMNAS_80 = 48;

    @Autowired
    private DevolucionService devolucionService;

    @Autowired
    private DevolucionItemService devolucionItemService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private PrintingService printingService;

    @Autowired
    private DevolucionConfiguracionService configuracionService;

    /** Una fila de etiqueta por item de la devolucion. */
    @Transactional(readOnly = true)
    public List<EtiquetaDevolucionFilaDto> construirFilas(Long devolucionId) {
        Devolucion d = devolucionService.findById(devolucionId)
                .orElseThrow(() -> new GraphQLException("Devolucion no encontrada: " + devolucionId));
        String identificador = d.getIdentificador() != null ? d.getIdentificador() : ("#" + d.getId());

        List<EtiquetaDevolucionFilaDto> filas = new ArrayList<>();
        for (DevolucionItem item : devolucionItemService.findByDevolucionId(devolucionId)) {
            Presentacion pres = item.getPresentacion();
            String codigo = codigoDe(pres);
            String producto = (item.getProducto() != null ? item.getProducto().getId() + " · "
                    + item.getProducto().getDescripcion() : "");
            String motivo = item.getMotivoAveria() != null ? item.getMotivoAveria().getDescripcion() : "";
            filas.add(new EtiquetaDevolucionFilaDto(
                    identificador, codigo, producto.trim(),
                    textoCantidad(item), motivo, identificador));
        }
        if (filas.isEmpty()) {
            throw new GraphQLException("La devolucion no tiene items para etiquetar");
        }
        return filas;
    }

    /** PDF A4 (Base64) con las etiquetas en 2 columnas cortables. */
    @Transactional(readOnly = true)
    public String generarPdf(Long devolucionId) {
        List<EtiquetaDevolucionFilaDto> filas = construirFilas(devolucionId);
        try {
            ClassPathResource resource = new ClassPathResource("reports/etiquetas-devolucion.jrxml");
            InputStream inputStream = resource.getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(filas);
            Map<String, Object> parameters = new HashMap<>();
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return Base64.getEncoder().encodeToString(pdfBytes);
        } catch (Exception e) {
            throw new GraphQLException("Error al generar las etiquetas PDF: " + e.getMessage());
        }
    }

    /** Imprime una etiqueta termica por item, cada una con su QR. */
    @Transactional(readOnly = true)
    public Boolean imprimirTicket(Long devolucionId, String printerName, Integer anchoMm) {
        List<EtiquetaDevolucionFilaDto> filas = construirFilas(devolucionId);
        DevolucionConfiguracion config = configuracionService.getConfiguracion();
        int ancho = anchoMm != null ? anchoMm
                : (config.getTicketAnchoMm() != null ? config.getTicketAnchoMm() : 58);
        int columnas = ancho <= 58 ? COLUMNAS_58 : COLUMNAS_80;
        String impresora = printerName != null ? printerName
                : (config.getImpresoraTicket() != null ? config.getImpresoraTicket() : IMPRESORA_DEFAULT);

        PrintService printService = printingService.getPrintService(impresora);
        if (printService == null) {
            printService = PrinterOutputStream.getPrintServiceByName(impresora);
        }
        if (printService == null) {
            throw new GraphQLException("No se encontro la impresora " + impresora);
        }

        try (PrinterOutputStream printerOutputStream = new PrinterOutputStream(printService)) {
            EscPos escpos = new EscPos(printerOutputStream);
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style centerBold = new Style().setJustification(EscPosConst.Justification.Center).setBold(true);
            QRCode qrCode = new QRCode();
            String sep = repetir("-", columnas);

            for (EtiquetaDevolucionFilaDto fila : filas) {
                escpos.writeLF(centerBold, recortar(fila.getIdentificador(), columnas));
                escpos.writeLF(sep);
                if (!fila.getCodigo().isEmpty()) escpos.writeLF(center, recortar(fila.getCodigo(), columnas));
                escpos.writeLF(recortar(fila.getProducto(), columnas * 2));
                escpos.writeLF(centerBold, recortar(fila.getCantidad(), columnas));
                if (!fila.getMotivo().isEmpty()) escpos.writeLF(center, recortar("Motivo: " + fila.getMotivo(), columnas));
                escpos.feed(1);
                escpos.write(qrCode.setSize(6).setJustification(EscPosConst.Justification.Center), fila.getQr());
                escpos.feed(1);
                escpos.writeLF(sep);
                escpos.feed(2);
            }
            escpos.feed(2);
            escpos.close();
        } catch (Exception e) {
            throw new GraphQLException("Error al imprimir las etiquetas: " + e.getMessage());
        }
        return true;
    }

    private String textoCantidad(DevolucionItem item) {
        double cantidad = item.getCantidad() != null ? item.getCantidad() : 0.0;
        Presentacion pres = item.getPresentacion();
        String presDesc = pres != null && pres.getDescripcion() != null ? pres.getDescripcion() : "";
        double factor = pres != null && pres.getCantidad() != null ? pres.getCantidad() : 1.0;
        double base = cantidad * factor;
        String factorTxt = PresentacionUtils.formatearFactor(pres);
        return (fmt(cantidad) + " " + presDesc + " " + factorTxt + " = " + fmt(base) + " UN").trim();
    }

    private String codigoDe(Presentacion pres) {
        if (pres == null || pres.getId() == null) return "";
        Codigo cod = codigoService.findPrincipalByPresentacionId(pres.getId());
        return cod != null && cod.getCodigo() != null ? cod.getCodigo() : "";
    }

    private String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private String recortar(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private String repetir(String s, int veces) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < veces; i++) sb.append(s);
        return sb.toString();
    }
}
