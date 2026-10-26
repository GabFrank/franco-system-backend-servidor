package com.franco.dev.service.impresion;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionConfiguracion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.dto.EtiquetaDevolucionDto;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Etiqueta de separado: <b>una por devolucion</b>, con un solo QR y la lista de
 * sus productos. Se imprime al marcar SEPARADO y se pega en la caja.
 *
 * El QR lleva el identificador (DEV-{sucursal}-{id}). Escanearlo trae la
 * devolucion entera con todos sus productos (PWA: boton de escanear y retiro a
 * proveedor), asi que una etiqueta por producto solo gastaba papel: antes se
 * imprimia una por item y todas llevaban el mismo QR.
 *
 * Dos salidas: ticket termico (ESC/POS) y PDF A4 (Jasper).
 *
 * Usado en:
 * - Desktop: Si (al separar y desde el detalle)
 * - Mobile: Si (PWA, PDF al separar)
 */
@Service
public class EtiquetaDevolucionService {

    private static final String IMPRESORA_DEFAULT = "TICKET58";
    private static final int COLUMNAS_58 = 32;
    private static final int COLUMNAS_80 = 48;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    /** La etiqueta de la devolucion, con todos sus productos. */
    @Transactional(readOnly = true)
    public EtiquetaDevolucionDto construirEtiqueta(Long devolucionId) {
        Devolucion d = devolucionService.findById(devolucionId)
                .orElseThrow(() -> new GraphQLException("Devolucion no encontrada: " + devolucionId));
        return armar(d, devolucionItemService.findByDevolucionId(devolucionId), this::codigoDe);
    }

    /**
     * Arma la etiqueta sin tocar la base: la devolucion y sus items ya cargados,
     * y como se obtiene el codigo de una presentacion.
     */
    static EtiquetaDevolucionDto armar(Devolucion d, List<DevolucionItem> items,
                                       Function<Presentacion, String> codigoDe) {
        if (items == null || items.isEmpty()) {
            throw new GraphQLException("La devolucion no tiene items para etiquetar");
        }
        String identificador = d.getIdentificador() != null ? d.getIdentificador() : ("#" + d.getId());

        List<EtiquetaDevolucionFilaDto> productos = new ArrayList<>();
        for (DevolucionItem item : items) {
            String codigo = codigoDe.apply(item.getPresentacion());
            String producto = item.getProducto() != null
                    ? item.getProducto().getId() + " · " + item.getProducto().getDescripcion() : "";
            String motivo = item.getMotivoAveria() != null && item.getMotivoAveria().getDescripcion() != null
                    ? item.getMotivoAveria().getDescripcion() : "";
            productos.add(new EtiquetaDevolucionFilaDto(codigo != null ? codigo : "",
                    producto.trim(), textoCantidad(item), motivo));
        }

        String sucursal = d.getSucursalOrigen() != null && d.getSucursalOrigen().getNombre() != null
                ? d.getSucursalOrigen().getNombre() : "";
        String proveedor = d.getProveedor() != null && d.getProveedor().getPersona() != null
                && d.getProveedor().getPersona().getNombre() != null
                ? d.getProveedor().getPersona().getNombre() : "Sin proveedor";
        String fecha = d.getFecha() != null ? d.getFecha().format(FECHA) : "";
        return new EtiquetaDevolucionDto(identificador, sucursal, proveedor, fecha, identificador, productos);
    }

    /** PDF A4 (Base64) con la etiqueta de la devolucion. */
    @Transactional(readOnly = true)
    public String generarPdf(Long devolucionId) {
        EtiquetaDevolucionDto etiqueta = construirEtiqueta(devolucionId);
        try {
            ClassPathResource resource = new ClassPathResource("reports/etiquetas-devolucion.jrxml");
            InputStream inputStream = resource.getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(etiqueta.getProductos());
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("identificador", etiqueta.getIdentificador());
            parameters.put("sucursal", etiqueta.getSucursal());
            parameters.put("proveedor", etiqueta.getProveedor());
            parameters.put("fecha", etiqueta.getFecha());
            parameters.put("qr", etiqueta.getQr());
            parameters.put("cantidadProductos", etiqueta.getProductos().size());
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return Base64.getEncoder().encodeToString(pdfBytes);
        } catch (Exception e) {
            throw new GraphQLException("Error al generar la etiqueta PDF: " + e.getMessage());
        }
    }

    /** Imprime la etiqueta en un solo ticket termico: cabecera, productos y un QR. */
    @Transactional(readOnly = true)
    public Boolean imprimirTicket(Long devolucionId, String printerName, Integer anchoMm) {
        EtiquetaDevolucionDto etiqueta = construirEtiqueta(devolucionId);
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
            Style bold = new Style().setBold(true);
            String sep = repetir("-", columnas);

            escpos.writeLF(centerBold, recortar(etiqueta.getIdentificador(), columnas));
            escpos.writeLF(center, recortar(etiqueta.getSucursal(), columnas));
            escpos.writeLF(center, recortar(etiqueta.getProveedor(), columnas));
            if (!etiqueta.getFecha().isEmpty()) escpos.writeLF(center, etiqueta.getFecha());
            escpos.writeLF(sep);
            for (EtiquetaDevolucionFilaDto p : etiqueta.getProductos()) {
                escpos.writeLF(bold, recortar(p.getProducto(), columnas * 2));
                escpos.writeLF(recortar(p.getCantidad(), columnas));
                if (!p.getMotivo().isEmpty()) escpos.writeLF(recortar("Motivo: " + p.getMotivo(), columnas));
            }
            escpos.writeLF(sep);
            escpos.writeLF(center, recortar(textoTotal(etiqueta.getProductos().size()), columnas));
            escpos.feed(1);
            escpos.write(new QRCode().setSize(6).setJustification(EscPosConst.Justification.Center),
                    etiqueta.getQr());
            escpos.feed(1);
            escpos.writeLF(center, recortar("Escanear para ver los productos", columnas));
            escpos.feed(4);
            escpos.close();
        } catch (Exception e) {
            throw new GraphQLException("Error al imprimir la etiqueta: " + e.getMessage());
        }
        return true;
    }

    static String textoTotal(int productos) {
        return productos == 1 ? "1 producto" : productos + " productos";
    }

    private static String textoCantidad(DevolucionItem item) {
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

    private static String fmt(double v) {
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
