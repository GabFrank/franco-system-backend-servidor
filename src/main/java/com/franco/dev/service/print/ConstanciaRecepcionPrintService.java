package com.franco.dev.service.print;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.empresarial.Sucursal;
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

            // Cargar template Jasper
            InputStream jasperTemplate = new ClassPathResource("reports/constancia-recepcion.jasper").getInputStream();

            // Compilar y llenar reporte
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperTemplate, parameters, dataSource);

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
        parameters.put("fechaRecepcion", recepcion.getFecha());
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
        parameters.put("totalCantidad", calcularTotalCantidad(recepcion));
        
        // Información del reporte
        parameters.put("tituloReporte", "CONSTANCIA DE RECEPCIÓN DE MERCADERÍA");
        parameters.put("fechaGeneracion", new Date());
        
        return parameters;
    }

    /**
     * Prepara datos para el reporte Jasper
     */
    private JRBeanCollectionDataSource prepararDataSource(RecepcionMercaderia recepcion) {
        List<Map<String, Object>> reportData = new ArrayList<>();
        
        // Obtener items de recepción usando el servicio
        List<RecepcionMercaderiaItem> items = recepcionMercaderiaItemService.findByRecepcionMercaderiaId(recepcion.getId());
        
        if (items != null) {
            for (RecepcionMercaderiaItem item : items) {
                Map<String, Object> row = new HashMap<>();
                
                // Información del producto
                if (item.getNotaRecepcionItem() != null && item.getNotaRecepcionItem().getProducto() != null) {
                    row.put("codigoProducto", item.getNotaRecepcionItem().getProducto().getId().toString());
                    row.put("nombreProducto", item.getNotaRecepcionItem().getProducto().getDescripcion());
                }
                
                // Información de la presentación
                if (item.getPresentacionRecibida() != null) {
                    row.put("presentacion", item.getPresentacionRecibida().getDescripcion());
                }
                
                // Cantidades
                row.put("cantidadEsperada", item.getNotaRecepcionItem() != null ? 
                    item.getNotaRecepcionItem().getCantidadEnNota() : 0.0);
                row.put("cantidadRecibida", item.getCantidadRecibida());
                row.put("cantidadRechazada", item.getCantidadRechazada() != null ? item.getCantidadRechazada() : 0.0);
                
                // Información de la nota
                if (item.getNotaRecepcionItem() != null && item.getNotaRecepcionItem().getNotaRecepcion() != null) {
                    row.put("numeroNota", item.getNotaRecepcionItem().getNotaRecepcion().getNumero());
                }
                
                // Método de verificación
                row.put("metodoVerificacion", item.getMetodoVerificacion() != null ? 
                    item.getMetodoVerificacion().toString() : "N/A");
                
                reportData.add(row);
            }
        }
        
        return new JRBeanCollectionDataSource(reportData);
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
     * Calcula el total de productos únicos
     */
    private int calcularTotalProductos(RecepcionMercaderia recepcion) {
        List<RecepcionMercaderiaItem> items = recepcionMercaderiaItemService.findByRecepcionMercaderiaId(recepcion.getId());
        if (items == null || items.isEmpty()) return 0;
        
        return (int) items.stream()
                .map(item -> item.getNotaRecepcionItem() != null ? 
                    item.getNotaRecepcionItem().getProducto() : null)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    /**
     * Calcula el total de cantidad recibida
     */
    private double calcularTotalCantidad(RecepcionMercaderia recepcion) {
        List<RecepcionMercaderiaItem> items = recepcionMercaderiaItemService.findByRecepcionMercaderiaId(recepcion.getId());
        if (items == null || items.isEmpty()) return 0.0;
        
        return items.stream()
                .mapToDouble(item -> item.getCantidadRecibida() != null ? item.getCantidadRecibida() : 0.0)
                .sum();
    }
} 