package com.franco.dev.service.print;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.operaciones.RecepcionMercaderiaService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.personas.ProveedorService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio para generar PDF de constancia de recepción usando Jasper Reports
 */
@Service
@AllArgsConstructor
@Slf4j
public class ConstanciaRecepcionPrintService {

    private final RecepcionMercaderiaService recepcionMercaderiaService;
    private final NotaRecepcionService notaRecepcionService;
    private final NotaRecepcionItemService notaRecepcionItemService;
    private final RecepcionMercaderiaItemService recepcionMercaderiaItemService;
    private final UsuarioService usuarioService;
    private final ProveedorService proveedorService;

    /**
     * Genera PDF de constancia de recepción
     */
    public byte[] generarConstanciaRecepcionPDF(Long recepcionId) throws Exception {
        try {
            // Obtener datos de la recepción
            RecepcionMercaderia recepcion = recepcionMercaderiaService.findById(recepcionId)
                    .orElseThrow(() -> new RuntimeException("Recepción no encontrada"));

            // Preparar datos para el reporte
            Map<String, Object> parameters = prepararParametrosReporte(recepcion);
            JRBeanCollectionDataSource dataSource = prepararDataSource(recepcion);

            // Cargar template .jrxml y compilar
            InputStream jrxmlStream = new ClassPathResource("reports/constancia-recepcion.jrxml").getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);

            // Llenar reporte
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            // Exportar a PDF
            return exportarAPDF(jasperPrint);

        } catch (Exception e) {
            log.error("Error generando constancia de recepción PDF", e);
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }

    /**
     * Prepara parámetros para el reporte Jasper
     */
    private Map<String, Object> prepararParametrosReporte(RecepcionMercaderia recepcion) {
        Map<String, Object> parameters = new HashMap<>();
        
        // Información básica de la recepción
        parameters.put("numeroRecepcion", recepcion.getId());
        parameters.put("fechaRecepcion", recepcion.getFecha() != null
                ? Date.from(recepcion.getFecha().atZone(ZoneId.systemDefault()).toInstant())
                : null);
        parameters.put("usuarioRecepcion", recepcion.getUsuario() != null && recepcion.getUsuario().getPersona() != null ? 
            recepcion.getUsuario().getPersona().getNombre() : "N/A");
        
        // Información de la sucursal
        if (recepcion.getSucursalRecepcion() != null) {
            parameters.put("nombreSucursal", recepcion.getSucursalRecepcion().getNombre());
            parameters.put("direccionSucursal", recepcion.getSucursalRecepcion().getDireccion());
        }
        
        // Información del proveedor (si está disponible)
        if (recepcion.getProveedor() != null && recepcion.getProveedor().getPersona() != null) {
            parameters.put("nombreProveedor", recepcion.getProveedor().getPersona().getNombre());
            parameters.put("rucProveedor", recepcion.getProveedor().getPersona().getDocumento());
        }
        
        // Totales - por ahora usamos valores por defecto ya que no tenemos relación directa
        parameters.put("totalNotas", 1); // TODO: Implementar cuando se agregue la relación
        parameters.put("totalProductos", calcularTotalProductos(recepcion));
        
        // Información del reporte
        parameters.put("tituloReporte", "CONSTANCIA DE RECEPCIÓN DE MERCADERÍA");
        parameters.put("fechaGeneracion", new Date());

        // QR mismo formato que otros reportes: frc-{sucursalId}-{TIPO}-{entityId}-{sucursalId}-{component}-null-null
        String sucursalId = recepcion.getSucursalRecepcion() != null ? recepcion.getSucursalRecepcion().getId().toString() : "0";
        parameters.put("qr", "frc-" + sucursalId + "-REC-" + recepcion.getId() + "-" + sucursalId + "-ConstanciaRecepcion-null-null");
        
        return parameters;
    }

    /**
     * Prepara datos para el reporte Jasper.
     * Agrupa por producto: misma presentación se suman cantidades; distintas presentaciones se muestran en unidad base.
     * Columna presentación muestra la cantidad de la presentación (ej. 12), no la descripción.
     */
    private JRBeanCollectionDataSource prepararDataSource(RecepcionMercaderia recepcion) {
        List<RecepcionMercaderiaItem> items = recepcionMercaderiaItemService.findByRecepcionMercaderiaId(recepcion.getId());
        if (items == null || items.isEmpty()) {
            return new JRBeanCollectionDataSource(Collections.emptyList());
        }

        // Agrupar por productId
        Map<Long, List<RecepcionMercaderiaItem>> porProducto = items.stream()
                .filter(item -> resolveProducto(item) != null)
                .collect(Collectors.groupingBy(item -> resolveProducto(item).getId()));

        List<Map<String, Object>> reportData = new ArrayList<>();
        for (Map.Entry<Long, List<RecepcionMercaderiaItem>> e : porProducto.entrySet()) {
            List<RecepcionMercaderiaItem> list = e.getValue();
            Producto producto = resolveProducto(list.get(0));
            if (producto == null) continue;

            Set<String> presentacionKeys = new HashSet<>();
            for (RecepcionMercaderiaItem it : list) {
                Presentacion p = it.getPresentacionRecibida();
                presentacionKeys.add(p != null ? p.getId().toString() : "n");
            }
            boolean mismaPresentacion = presentacionKeys.size() == 1;

            if (mismaPresentacion) {
                // Una fila: sumar cantidades
                double sumEsperada = 0, sumRecibida = 0, sumRechazada = 0;
                Double presentacionCantidad = null;
                for (RecepcionMercaderiaItem it : list) {
                    double esperada = it.getNotaRecepcionItem() != null && it.getNotaRecepcionItem().getCantidadEnNota() != null
                            ? it.getNotaRecepcionItem().getCantidadEnNota() : 0.0;
                    sumEsperada += esperada;
                    sumRecibida += it.getCantidadRecibida() != null ? it.getCantidadRecibida() : 0.0;
                    sumRechazada += it.getCantidadRechazada() != null ? it.getCantidadRechazada() : 0.0;
                    if (it.getPresentacionRecibida() != null && it.getPresentacionRecibida().getCantidad() != null) {
                        presentacionCantidad = it.getPresentacionRecibida().getCantidad();
                    }
                }
                if (presentacionCantidad == null) presentacionCantidad = 1.0;
                reportData.add(crearFila(producto, presentacionCantidad, sumEsperada, sumRecibida, sumRechazada, list.get(0)));
            } else {
                // Distintas presentaciones: una fila en unidad base
                double baseEsperada = 0, baseRecibida = 0, baseRechazada = 0;
                for (RecepcionMercaderiaItem it : list) {
                    double mult = it.getPresentacionRecibida() != null && it.getPresentacionRecibida().getCantidad() != null
                            ? it.getPresentacionRecibida().getCantidad() : 1.0;
                    double esperada = it.getNotaRecepcionItem() != null && it.getNotaRecepcionItem().getCantidadEnNota() != null
                            ? it.getNotaRecepcionItem().getCantidadEnNota() : 0.0;
                    baseEsperada += esperada * mult;
                    baseRecibida += (it.getCantidadRecibida() != null ? it.getCantidadRecibida() : 0.0) * mult;
                    baseRechazada += (it.getCantidadRechazada() != null ? it.getCantidadRechazada() : 0.0) * mult;
                }
                reportData.add(crearFila(producto, 1.0, baseEsperada, baseRecibida, baseRechazada, list.get(0)));
            }
        }

        return new JRBeanCollectionDataSource(reportData);
    }

    private Producto resolveProducto(RecepcionMercaderiaItem item) {
        if (item.getProducto() != null) return item.getProducto();
        return item.getNotaRecepcionItem() != null ? item.getNotaRecepcionItem().getProducto() : null;
    }

    private Map<String, Object> crearFila(Producto producto, Double presentacionCantidad, double cantidadEsperada, double cantidadRecibida, double cantidadRechazada, RecepcionMercaderiaItem sample) {
        Map<String, Object> row = new HashMap<>();
        row.put("codigoProducto", producto.getId().toString());
        row.put("nombreProducto", producto.getDescripcion());
        row.put("presentacion", presentacionCantidad);
        row.put("cantidadEsperada", cantidadEsperada);
        row.put("cantidadRecibida", cantidadRecibida);
        row.put("cantidadRechazada", cantidadRechazada);
        row.put("numeroNota", sample.getNotaRecepcionItem() != null && sample.getNotaRecepcionItem().getNotaRecepcion() != null
                ? sample.getNotaRecepcionItem().getNotaRecepcion().getNumero() : null);
        row.put("metodoVerificacion", sample.getMetodoVerificacion() != null ? sample.getMetodoVerificacion().toString() : "N/A");
        return row;
    }

    /**
     * Exporta el reporte Jasper a PDF
     */
    private byte[] exportarAPDF(JasperPrint jasperPrint) throws JRException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
        
        exporter.exportReport();
        
        return outputStream.toByteArray();
    }

    /**
     * Calcula el total de productos únicos.
     * Usa item.getProducto() (campo directo en RecepcionMercaderiaItem) y, si es null, item.getNotaRecepcionItem().getProducto().
     */
    private int calcularTotalProductos(RecepcionMercaderia recepcion) {
        List<RecepcionMercaderiaItem> items = recepcionMercaderiaItemService.findByRecepcionMercaderiaId(recepcion.getId());
        if (items == null || items.isEmpty()) {
            log.debug("calcularTotalProductos: recepcionId={} sin items", recepcion.getId());
            return 0;
        }
        log.debug("calcularTotalProductos: recepcionId={}, cantidad items={}", recepcion.getId(), items.size());

        Set<Long> productoIds = new HashSet<>();
        for (RecepcionMercaderiaItem item : items) {
            Producto producto = item.getProducto();
            if (producto == null && item.getNotaRecepcionItem() != null) {
                producto = item.getNotaRecepcionItem().getProducto();
            }
            if (producto == null) {
                log.warn("calcularTotalProductos: item id={} tiene producto null (notaRecepcionItem={})",
                        item.getId(),
                        item.getNotaRecepcionItem() != null ? item.getNotaRecepcionItem().getId() : "null");
                continue;
            }
            productoIds.add(producto.getId());
        }
        return productoIds.size();
    }

} 