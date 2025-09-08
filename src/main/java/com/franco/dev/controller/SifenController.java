package com.franco.dev.controller;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/sifen")
@RequiredArgsConstructor
@Slf4j
public class SifenController {

    private final FacturaLegalService facturaLegalService;
    private final FacturaLegalItemService facturaLegalItemService;

    /**
     * Endpoint para consultar factura legal con timbrado para SIFEN
     * Usado por el microservicio Node.js
     */
    @GetMapping("/factura/{facturaId}/sucursal/{sucursalId}")
    public ResponseEntity<Map<String, Object>> getFacturaParaSifen(
            @PathVariable Long facturaId,
            @PathVariable Long sucursalId) {
        
        try {
            log.info("Consultando factura para SIFEN: facturaId={}, sucursalId={}", facturaId, sucursalId);
            
            FacturaLegal factura = facturaLegalService.findByIdWithTimbradoAndItems(facturaId, sucursalId);
            
            if (factura == null) {
                log.warn("Factura no encontrada: facturaId={}, sucursalId={}", facturaId, sucursalId);
                return ResponseEntity.notFound().build();
            }
            
            // Construir respuesta estructurada para SIFEN
            Map<String, Object> respuesta = new HashMap<>();
            
            // Datos del emisor (desde timbrado)
            Map<String, Object> emisor = new HashMap<>();
            if (factura.getTimbradoDetalle() != null && factura.getTimbradoDetalle().getTimbrado() != null) {
                com.franco.dev.domain.financiero.Timbrado timbrado = factura.getTimbradoDetalle().getTimbrado();
                emisor.put("ruc", timbrado.getRuc());
                emisor.put("razonSocial", timbrado.getRazonSocial());
                emisor.put("nombreComercial", timbrado.getRazonSocial());
                emisor.put("direccion", "Dirección de la empresa"); // TODO: Obtener de configuración
                emisor.put("telefono", "021-123456"); // TODO: Obtener de configuración
                emisor.put("email", "info@empresa.com.py"); // TODO: Obtener de configuración
            }
            respuesta.put("emisor", emisor);
            
            // Datos del receptor (desde cliente)
            Map<String, Object> receptor = new HashMap<>();
            if (factura.getCliente() != null) {
                receptor.put("ruc", factura.getRuc());
                receptor.put("razonSocial", factura.getNombre());
                receptor.put("direccion", factura.getDireccion());
                receptor.put("telefono", ""); // TODO: Obtener de cliente
                receptor.put("email", ""); // TODO: Obtener de cliente
            }
            respuesta.put("receptor", receptor);
            
            // Datos del documento
            Map<String, Object> documento = new HashMap<>();
            documento.put("tipo", "01"); // 01 = Factura Electrónica según Manual Técnico SIFEN v150
            documento.put("numero", factura.getNumeroFactura());
            documento.put("fecha", factura.getFecha());
            documento.put("moneda", "PYG");
            documento.put("tipoCambio", 1);
            documento.put("condicionOperacion", factura.getCredito() ? 2 : 1); // 1=Contado, 2=Crédito
            documento.put("plazo", 0);
            
            // Información del timbrado
            if (factura.getTimbradoDetalle() != null) {
                documento.put("numeroTimbrado", factura.getTimbradoDetalle().getTimbrado().getNumero());
                documento.put("puntoExpedicion", factura.getTimbradoDetalle().getPuntoExpedicion());
                documento.put("serie", factura.getTimbradoDetalle().getPuntoExpedicion());
            }
            
            respuesta.put("documento", documento);
            
            // Items del documento
            List<Map<String, Object>> items = new ArrayList<>();
            List<FacturaLegalItem> facturaItems = facturaLegalItemService.findByFacturaLegalId(facturaId, sucursalId);
            if (facturaItems != null) {
                for (FacturaLegalItem item : facturaItems) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("codigo", item.getPresentacion() != null ? item.getPresentacion().getId().toString() : "PROD" + item.getId());
                    itemMap.put("descripcion", item.getDescripcion());
                    itemMap.put("cantidad", item.getCantidad());
                    itemMap.put("precioUnitario", item.getPrecioUnitario());
                    itemMap.put("descuento", 0.0);
                    
                    // Calcular IVA según el total parcial
                    double iva = 0.0;
                    if (factura.getTotalParcial10() != null && factura.getTotalParcial10() > 0) {
                        iva = factura.getTotalParcial10() * 0.1; // 10%
                    } else if (factura.getTotalParcial5() != null && factura.getTotalParcial5() > 0) {
                        iva = factura.getTotalParcial5() * 0.05; // 5%
                    }
                    itemMap.put("iva", iva);
                    itemMap.put("total", item.getTotal());
                    items.add(itemMap);
                }
            }
            respuesta.put("items", items);
            
            // Totales
            Map<String, Object> totales = new HashMap<>();
            totales.put("gravada10", factura.getTotalParcial10() != null ? factura.getTotalParcial10() : 0.0);
            totales.put("gravada5", factura.getTotalParcial5() != null ? factura.getTotalParcial5() : 0.0);
            totales.put("exenta", factura.getTotalParcial0() != null ? factura.getTotalParcial0() : 0.0);
            totales.put("iva10", factura.getIvaParcial10() != null ? factura.getIvaParcial10() : 0.0);
            totales.put("iva5", factura.getIvaParcial5() != null ? factura.getIvaParcial5() : 0.0);
            totales.put("total", factura.getTotalFinal());
            respuesta.put("totales", totales);
            
            log.info("Factura consultada exitosamente para SIFEN: facturaId={}", facturaId);
            return ResponseEntity.ok(respuesta);
            
        } catch (Exception e) {
            log.error("Error consultando factura para SIFEN: facturaId={}, sucursalId={}", facturaId, sucursalId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error consultando factura: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Endpoint de prueba para verificar que el controlador funcione
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "SifenController funcionando correctamente");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("factura", "/api/sifen/factura/{facturaId}/sucursal/{sucursalId}");
        endpoints.put("test", "/api/sifen/test");
        response.put("endpoints", endpoints);
        
        return ResponseEntity.ok(response);
    }
}
