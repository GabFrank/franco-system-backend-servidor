package com.franco.dev.service.sifen.validation;

import com.franco.dev.service.sifen.dto.request.FacturaElectronicaRequest;
import com.franco.dev.service.sifen.dto.request.ItemFacturaRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Validador de totales y cálculos para facturación electrónica
 */
public class TotalesValidator {
    
    private static final BigDecimal IVA_5 = new BigDecimal("0.05");
    private static final BigDecimal IVA_10 = new BigDecimal("0.10");
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01"); // Tolerancia de 1 centavo
    
    /**
     * Valida todos los totales de la factura
     */
    public ValidationResult validarTotales(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        // Validar totales de items
        ValidationResult itemsResult = validarTotalesItems(request.getItems());
        result.addErrors(itemsResult.getErrors());
        result.addWarnings(itemsResult.getWarnings());
        
        // Validar totales generales
        ValidationResult generalesResult = validarTotalesGenerales(request);
        result.addErrors(generalesResult.getErrors());
        result.addWarnings(generalesResult.getWarnings());
        
        // Validar consistencia entre items y totales generales
        ValidationResult consistenciaResult = validarConsistenciaTotales(request);
        result.addErrors(consistenciaResult.getErrors());
        result.addWarnings(consistenciaResult.getWarnings());
        
        return result;
    }
    
    /**
     * Valida los totales de los items individuales
     */
    private ValidationResult validarTotalesItems(List<ItemFacturaRequest> items) {
        ValidationResult result = new ValidationResult();
        
        if (items == null || items.isEmpty()) {
            result.addError("La factura debe tener al menos un item");
            return result;
        }
        
        if (items.size() > 100) {
            result.addError("La factura no puede tener más de 100 items");
            return result;
        }
        
        for (int i = 0; i < items.size(); i++) {
            ItemFacturaRequest item = items.get(i);
            ValidationResult itemResult = validarItem(item, i + 1);
            result.addErrors(itemResult.getErrors());
            result.addWarnings(itemResult.getWarnings());
        }
        
        return result;
    }
    
    /**
     * Valida un item individual
     */
    private ValidationResult validarItem(ItemFacturaRequest item, int numeroItem) {
        ValidationResult result = new ValidationResult();
        
        // Validar campos obligatorios
        if (item.getCodigoProducto() == null || item.getCodigoProducto().trim().isEmpty()) {
            result.addError("Item " + numeroItem + ": Código de producto es obligatorio");
        }
        
        if (item.getDescripcion() == null || item.getDescripcion().trim().isEmpty()) {
            result.addError("Item " + numeroItem + ": Descripción es obligatoria");
        }
        
        if (item.getCantidad() == null || item.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("Item " + numeroItem + ": Cantidad debe ser mayor a 0");
        }
        
        if (item.getPrecioUnitario() == null || item.getPrecioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("Item " + numeroItem + ": Precio unitario debe ser mayor a 0");
        }
        
        if (item.getTipoImpuesto() == null || item.getTipoImpuesto().trim().isEmpty()) {
            result.addError("Item " + numeroItem + ": Tipo de impuesto es obligatorio");
        }
        
        if (item.getPorcentajeImpuesto() == null) {
            result.addError("Item " + numeroItem + ": Porcentaje de impuesto es obligatorio");
        }
        
        // Validar cálculos del item
        if (item.getCantidad() != null && item.getPrecioUnitario() != null) {
            BigDecimal subtotalCalculado = item.getCantidad().multiply(item.getPrecioUnitario());
            
            if (item.getDescuento() != null) {
                subtotalCalculado = subtotalCalculado.subtract(item.getDescuento());
            }
            
            if (item.getSubtotal() != null) {
                if (!sonIguales(subtotalCalculado, item.getSubtotal())) {
                    result.addError("Item " + numeroItem + ": Subtotal incorrecto. Esperado: " + 
                        subtotalCalculado + ", Actual: " + item.getSubtotal());
                }
            }
            
            // Validar cálculo de IVA
            if (item.getTipoImpuesto() != null && item.getPorcentajeImpuesto() != null) {
                BigDecimal ivaCalculado = calcularIva(subtotalCalculado, item.getTipoImpuesto(), item.getPorcentajeImpuesto());
                
                if (item.getMontoImpuesto() != null) {
                    if (!sonIguales(ivaCalculado, item.getMontoImpuesto())) {
                        result.addError("Item " + numeroItem + ": Monto de IVA incorrecto. Esperado: " + 
                            ivaCalculado + ", Actual: " + item.getMontoImpuesto());
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Valida los totales generales de la factura
     */
    private ValidationResult validarTotalesGenerales(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        // Validar que los totales no sean negativos
        if (request.getTotalGravado10() != null && request.getTotalGravado10().compareTo(BigDecimal.ZERO) < 0) {
            result.addError("Total gravado 10% no puede ser negativo");
        }
        
        if (request.getTotalGravado5() != null && request.getTotalGravado5().compareTo(BigDecimal.ZERO) < 0) {
            result.addError("Total gravado 5% no puede ser negativo");
        }
        
        if (request.getTotalExento() != null && request.getTotalExento().compareTo(BigDecimal.ZERO) < 0) {
            result.addError("Total exento no puede ser negativo");
        }
        
        if (request.getTotalIva10() != null && request.getTotalIva10().compareTo(BigDecimal.ZERO) < 0) {
            result.addError("Total IVA 10% no puede ser negativo");
        }
        
        if (request.getTotalIva5() != null && request.getTotalIva5().compareTo(BigDecimal.ZERO) < 0) {
            result.addError("Total IVA 5% no puede ser negativo");
        }
        
        if (request.getTotalGeneral() != null && request.getTotalGeneral().compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("Total general debe ser mayor a 0");
        }
        
        return result;
    }
    
    /**
     * Valida la consistencia entre items y totales generales
     */
    private ValidationResult validarConsistenciaTotales(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return result;
        }
        
        // Calcular totales desde los items
        BigDecimal totalGravado10Calculado = BigDecimal.ZERO;
        BigDecimal totalGravado5Calculado = BigDecimal.ZERO;
        BigDecimal totalExentoCalculado = BigDecimal.ZERO;
        BigDecimal totalIva10Calculado = BigDecimal.ZERO;
        BigDecimal totalIva5Calculado = BigDecimal.ZERO;
        BigDecimal totalGeneralCalculado = BigDecimal.ZERO;
        
        for (ItemFacturaRequest item : request.getItems()) {
            if (item.getSubtotal() != null) {
                if ("10".equals(item.getTipoImpuesto())) {
                    totalGravado10Calculado = totalGravado10Calculado.add(item.getSubtotal());
                    if (item.getMontoImpuesto() != null) {
                        totalIva10Calculado = totalIva10Calculado.add(item.getMontoImpuesto());
                    }
                } else if ("5".equals(item.getTipoImpuesto())) {
                    totalGravado5Calculado = totalGravado5Calculado.add(item.getSubtotal());
                    if (item.getMontoImpuesto() != null) {
                        totalIva5Calculado = totalIva5Calculado.add(item.getMontoImpuesto());
                    }
                } else if ("0".equals(item.getTipoImpuesto())) {
                    totalExentoCalculado = totalExentoCalculado.add(item.getSubtotal());
                }
            }
        }
        
        totalGeneralCalculado = totalGravado10Calculado.add(totalGravado5Calculado)
            .add(totalExentoCalculado).add(totalIva10Calculado).add(totalIva5Calculado);
        
        // Comparar con totales declarados
        if (request.getTotalGravado10() != null && !sonIguales(totalGravado10Calculado, request.getTotalGravado10())) {
            result.addError("Total gravado 10% no coincide con la suma de items. Esperado: " + 
                totalGravado10Calculado + ", Actual: " + request.getTotalGravado10());
        }
        
        if (request.getTotalGravado5() != null && !sonIguales(totalGravado5Calculado, request.getTotalGravado5())) {
            result.addError("Total gravado 5% no coincide con la suma de items. Esperado: " + 
                totalGravado5Calculado + ", Actual: " + request.getTotalGravado5());
        }
        
        if (request.getTotalExento() != null && !sonIguales(totalExentoCalculado, request.getTotalExento())) {
            result.addError("Total exento no coincide con la suma de items. Esperado: " + 
                totalExentoCalculado + ", Actual: " + request.getTotalExento());
        }
        
        if (request.getTotalIva10() != null && !sonIguales(totalIva10Calculado, request.getTotalIva10())) {
            result.addError("Total IVA 10% no coincide con la suma de items. Esperado: " + 
                totalIva10Calculado + ", Actual: " + request.getTotalIva10());
        }
        
        if (request.getTotalIva5() != null && !sonIguales(totalIva5Calculado, request.getTotalIva5())) {
            result.addError("Total IVA 5% no coincide con la suma de items. Esperado: " + 
                totalIva5Calculado + ", Actual: " + request.getTotalIva5());
        }
        
        if (request.getTotalGeneral() != null && !sonIguales(totalGeneralCalculado, request.getTotalGeneral())) {
            result.addError("Total general no coincide con la suma de items. Esperado: " + 
                totalGeneralCalculado + ", Actual: " + request.getTotalGeneral());
        }
        
        return result;
    }
    
    /**
     * Calcula el IVA para un item
     */
    private BigDecimal calcularIva(BigDecimal subtotal, String tipoImpuesto, BigDecimal porcentajeImpuesto) {
        if (subtotal == null || tipoImpuesto == null || porcentajeImpuesto == null) {
            return BigDecimal.ZERO;
        }
        
        if ("0".equals(tipoImpuesto)) {
            return BigDecimal.ZERO;
        }
        
        return subtotal.multiply(porcentajeImpuesto.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
            .setScale(0, RoundingMode.HALF_UP);
    }
    
    /**
     * Compara dos BigDecimal con tolerancia
     */
    private boolean sonIguales(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        return a.subtract(b).abs().compareTo(TOLERANCIA) <= 0;
    }
}
