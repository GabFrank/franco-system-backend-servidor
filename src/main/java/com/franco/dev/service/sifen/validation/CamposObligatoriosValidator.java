package com.franco.dev.service.sifen.validation;

import com.franco.dev.service.sifen.dto.request.FacturaElectronicaRequest;
import com.franco.dev.service.sifen.dto.request.ItemFacturaRequest;

import java.util.List;

/**
 * Validador de campos obligatorios para facturación electrónica
 */
public class CamposObligatoriosValidator {
    
    /**
     * Valida todos los campos obligatorios de la factura
     */
    public ValidationResult validarCamposObligatorios(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        // Validar campos del emisor
        ValidationResult emisorResult = validarCamposEmisor(request);
        result.addErrors(emisorResult.getErrors());
        result.addWarnings(emisorResult.getWarnings());
        
        // Validar campos del receptor
        ValidationResult receptorResult = validarCamposReceptor(request);
        result.addErrors(receptorResult.getErrors());
        result.addWarnings(receptorResult.getWarnings());
        
        // Validar campos del documento
        ValidationResult documentoResult = validarCamposDocumento(request);
        result.addErrors(documentoResult.getErrors());
        result.addWarnings(documentoResult.getWarnings());
        
        // Validar campos de los items
        ValidationResult itemsResult = validarCamposItems(request.getItems());
        result.addErrors(itemsResult.getErrors());
        result.addWarnings(itemsResult.getWarnings());
        
        return result;
    }
    
    /**
     * Valida campos obligatorios del emisor
     */
    private ValidationResult validarCamposEmisor(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        if (request.getRucEmisor() == null || request.getRucEmisor().trim().isEmpty()) {
            result.addError("RUC del emisor es obligatorio");
        }
        
        if (request.getRazonSocialEmisor() == null || request.getRazonSocialEmisor().trim().isEmpty()) {
            result.addError("Razón social del emisor es obligatoria");
        }
        
        if (request.getDireccionEmisor() == null || request.getDireccionEmisor().trim().isEmpty()) {
            result.addError("Dirección del emisor es obligatoria");
        }
        
        if (request.getTelefonoEmisor() == null || request.getTelefonoEmisor().trim().isEmpty()) {
            result.addWarning("Teléfono del emisor es recomendado");
        }
        
        if (request.getEmailEmisor() == null || request.getEmailEmisor().trim().isEmpty()) {
            result.addWarning("Email del emisor es recomendado");
        }
        
        return result;
    }
    
    /**
     * Valida campos obligatorios del receptor
     */
    private ValidationResult validarCamposReceptor(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        if (request.getRucReceptor() == null || request.getRucReceptor().trim().isEmpty()) {
            result.addError("RUC del receptor es obligatorio");
        }
        
        if (request.getRazonSocialReceptor() == null || request.getRazonSocialReceptor().trim().isEmpty()) {
            result.addError("Razón social del receptor es obligatoria");
        }
        
        if (request.getDireccionReceptor() == null || request.getDireccionReceptor().trim().isEmpty()) {
            result.addError("Dirección del receptor es obligatoria");
        }
        
        if (request.getTelefonoReceptor() == null || request.getTelefonoReceptor().trim().isEmpty()) {
            result.addWarning("Teléfono del receptor es recomendado");
        }
        
        if (request.getEmailReceptor() == null || request.getEmailReceptor().trim().isEmpty()) {
            result.addWarning("Email del receptor es recomendado");
        }
        
        return result;
    }
    
    /**
     * Valida campos obligatorios del documento
     */
    private ValidationResult validarCamposDocumento(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        if (request.getTipoDocumento() == null || request.getTipoDocumento().trim().isEmpty()) {
            result.addError("Tipo de documento es obligatorio");
        }
        
        if (request.getNumeroDocumento() == null || request.getNumeroDocumento().trim().isEmpty()) {
            result.addError("Número de documento es obligatorio");
        }
        
        if (request.getFechaEmision() == null) {
            result.addError("Fecha de emisión es obligatoria");
        }
        
        if (request.getMoneda() == null || request.getMoneda().trim().isEmpty()) {
            result.addError("Moneda es obligatoria");
        }
        
        if (request.getTipoCambio() == null) {
            result.addError("Tipo de cambio es obligatorio");
        }
        
        if (request.getCondicionVenta() == null || request.getCondicionVenta().trim().isEmpty()) {
            result.addError("Condición de venta es obligatoria");
        }
        
        if (request.getTipoPago() == null || request.getTipoPago().trim().isEmpty()) {
            result.addError("Tipo de pago es obligatorio");
        }
        
        if (request.getTotalGeneral() == null) {
            result.addError("Total general es obligatorio");
        }
        
        return result;
    }
    
    /**
     * Valida campos obligatorios de los items
     */
    private ValidationResult validarCamposItems(List<ItemFacturaRequest> items) {
        ValidationResult result = new ValidationResult();
        
        if (items == null || items.isEmpty()) {
            result.addError("La factura debe tener al menos un item");
            return result;
        }
        
        for (int i = 0; i < items.size(); i++) {
            ItemFacturaRequest item = items.get(i);
            ValidationResult itemResult = validarCamposItem(item, i + 1);
            result.addErrors(itemResult.getErrors());
            result.addWarnings(itemResult.getWarnings());
        }
        
        return result;
    }
    
    /**
     * Valida campos obligatorios de un item individual
     */
    private ValidationResult validarCamposItem(ItemFacturaRequest item, int numeroItem) {
        ValidationResult result = new ValidationResult();
        
        if (item.getCodigoProducto() == null || item.getCodigoProducto().trim().isEmpty()) {
            result.addError("Item " + numeroItem + ": Código de producto es obligatorio");
        }
        
        if (item.getDescripcion() == null || item.getDescripcion().trim().isEmpty()) {
            result.addError("Item " + numeroItem + ": Descripción es obligatoria");
        }
        
        if (item.getCantidad() == null) {
            result.addError("Item " + numeroItem + ": Cantidad es obligatoria");
        }
        
        if (item.getPrecioUnitario() == null) {
            result.addError("Item " + numeroItem + ": Precio unitario es obligatorio");
        }
        
        if (item.getSubtotal() == null) {
            result.addError("Item " + numeroItem + ": Subtotal es obligatorio");
        }
        
        if (item.getTipoImpuesto() == null || item.getTipoImpuesto().trim().isEmpty()) {
            result.addError("Item " + numeroItem + ": Tipo de impuesto es obligatorio");
        }
        
        if (item.getPorcentajeImpuesto() == null) {
            result.addError("Item " + numeroItem + ": Porcentaje de impuesto es obligatorio");
        }
        
        if (item.getMontoImpuesto() == null) {
            result.addError("Item " + numeroItem + ": Monto de impuesto es obligatorio");
        }
        
        if (item.getUnidadMedida() == null || item.getUnidadMedida().trim().isEmpty()) {
            result.addWarning("Item " + numeroItem + ": Unidad de medida es recomendada");
        }
        
        return result;
    }
}
