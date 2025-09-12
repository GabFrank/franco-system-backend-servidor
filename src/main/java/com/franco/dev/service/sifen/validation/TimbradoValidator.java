package com.franco.dev.service.sifen.validation;

import com.franco.dev.service.sifen.dto.request.FacturaElectronicaRequest;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Validador de timbrado para facturación electrónica
 */
public class TimbradoValidator {
    
    private static final Pattern ESTABLECIMIENTO_PATTERN = Pattern.compile("^\\d{3}$");
    private static final Pattern PUNTO_EXPEDICION_PATTERN = Pattern.compile("^\\d{3}$");
    private static final Pattern NUMERO_DOCUMENTO_PATTERN = Pattern.compile("^\\d{6}$");
    
    /**
     * Valida los datos de timbrado
     */
    public ValidationResult validarTimbrado(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        // Validar códigos de establecimiento y punto de expedición
        ValidationResult codigosResult = validarCodigosEstablecimiento(request);
        result.addErrors(codigosResult.getErrors());
        result.addWarnings(codigosResult.getWarnings());
        
        // Validar número de documento
        ValidationResult numeroResult = validarNumeroDocumento(request);
        result.addErrors(numeroResult.getErrors());
        result.addWarnings(numeroResult.getWarnings());
        
        // Validar fecha de emisión vs timbrado
        ValidationResult fechaResult = validarFechaEmision(request);
        result.addErrors(fechaResult.getErrors());
        result.addWarnings(fechaResult.getWarnings());
        
        return result;
    }
    
    /**
     * Valida los códigos de establecimiento y punto de expedición
     */
    private ValidationResult validarCodigosEstablecimiento(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        // Validar código de establecimiento
        if (request.getCodigoEstablecimiento() == null || request.getCodigoEstablecimiento().trim().isEmpty()) {
            result.addError("Código de establecimiento es obligatorio");
        } else {
            String codigoEst = request.getCodigoEstablecimiento().trim();
            if (!ESTABLECIMIENTO_PATTERN.matcher(codigoEst).matches()) {
                result.addError("Código de establecimiento debe tener 3 dígitos: " + codigoEst);
            } else {
                int est = Integer.parseInt(codigoEst);
                if (est < 1 || est > 999) {
                    result.addError("Código de establecimiento debe estar entre 001 y 999");
                }
            }
        }
        
        // Validar código de punto de expedición
        if (request.getCodigoPuntoExpedicion() == null || request.getCodigoPuntoExpedicion().trim().isEmpty()) {
            result.addError("Código de punto de expedición es obligatorio");
        } else {
            String codigoPunto = request.getCodigoPuntoExpedicion().trim();
            if (!PUNTO_EXPEDICION_PATTERN.matcher(codigoPunto).matches()) {
                result.addError("Código de punto de expedición debe tener 3 dígitos: " + codigoPunto);
            } else {
                int punto = Integer.parseInt(codigoPunto);
                if (punto < 1 || punto > 999) {
                    result.addError("Código de punto de expedición debe estar entre 001 y 999");
                }
            }
        }
        
        return result;
    }
    
    /**
     * Valida el número de documento
     */
    private ValidationResult validarNumeroDocumento(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        if (request.getNumeroDocumento() == null || request.getNumeroDocumento().trim().isEmpty()) {
            result.addError("Número de documento es obligatorio");
            return result;
        }
        
        String numeroDoc = request.getNumeroDocumento().trim();
        
        // Validar formato completo: 001-001-000001
        if (!numeroDoc.matches("^\\d{3}-\\d{3}-\\d{6}$")) {
            result.addError("Formato de número de documento inválido. Debe ser: 001-001-000001");
            return result;
        }
        
        // Extraer partes del número
        String[] partes = numeroDoc.split("-");
        String establecimiento = partes[0];
        String puntoExpedicion = partes[1];
        String numero = partes[2];
        
        // Validar que coincida con los códigos de establecimiento y punto
        if (request.getCodigoEstablecimiento() != null && 
            !establecimiento.equals(request.getCodigoEstablecimiento().trim())) {
            result.addError("Establecimiento en número de documento no coincide con código de establecimiento");
        }
        
        if (request.getCodigoPuntoExpedicion() != null && 
            !puntoExpedicion.equals(request.getCodigoPuntoExpedicion().trim())) {
            result.addError("Punto de expedición en número de documento no coincide con código de punto de expedición");
        }
        
        // Validar número secuencial
        if (!NUMERO_DOCUMENTO_PATTERN.matcher(numero).matches()) {
            result.addError("Número de documento debe tener 6 dígitos");
        } else {
            int numDoc = Integer.parseInt(numero);
            if (numDoc < 1) {
                result.addError("Número de documento debe ser mayor a 0");
            }
        }
        
        return result;
    }
    
    /**
     * Valida la fecha de emisión en relación al timbrado
     */
    private ValidationResult validarFechaEmision(FacturaElectronicaRequest request) {
        ValidationResult result = new ValidationResult();
        
        if (request.getFechaEmision() == null) {
            result.addError("Fecha de emisión es obligatoria");
            return result;
        }
        
        LocalDateTime fechaEmision = request.getFechaEmision();
        LocalDateTime ahora = LocalDateTime.now();
        
        // No puede ser futura
        if (fechaEmision.isAfter(ahora)) {
            result.addError("Fecha de emisión no puede ser futura");
        }
        
        // No puede ser muy antigua (más de 30 días)
        if (fechaEmision.isBefore(ahora.minusDays(30))) {
            result.addWarning("Fecha de emisión es muy antigua (más de 30 días)");
        }
        
        // Validar que sea un día hábil (opcional)
        int diaSemana = fechaEmision.getDayOfWeek().getValue();
        if (diaSemana == 6 || diaSemana == 7) {
            result.addWarning("Fecha de emisión es un fin de semana");
        }
        
        return result;
    }
    
    /**
     * Valida el rango de numeración del timbrado
     */
    public ValidationResult validarRangoNumeracion(FacturaElectronicaRequest request, 
                                                   int numeroInicio, int numeroFin) {
        ValidationResult result = new ValidationResult();
        
        if (request.getNumeroDocumento() == null) {
            return result;
        }
        
        try {
            String numeroDoc = request.getNumeroDocumento().trim();
            String[] partes = numeroDoc.split("-");
            int numero = Integer.parseInt(partes[2]);
            
            if (numero < numeroInicio) {
                result.addError("Número de documento está fuera del rango autorizado. Mínimo: " + numeroInicio);
            }
            
            if (numero > numeroFin) {
                result.addError("Número de documento está fuera del rango autorizado. Máximo: " + numeroFin);
            }
            
        } catch (Exception e) {
            result.addError("Error al validar rango de numeración: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Valida la vigencia del timbrado
     */
    public ValidationResult validarVigenciaTimbrado(LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        ValidationResult result = new ValidationResult();
        
        LocalDateTime ahora = LocalDateTime.now();
        
        if (ahora.isBefore(fechaInicio)) {
            result.addError("El timbrado aún no está vigente. Fecha de inicio: " + fechaInicio);
        }
        
        if (ahora.isAfter(fechaFin)) {
            result.addError("El timbrado ha expirado. Fecha de fin: " + fechaFin);
        }
        
        // Advertencia si está próximo a expirar (30 días)
        if (ahora.isAfter(fechaFin.minusDays(30))) {
            result.addWarning("El timbrado expira pronto. Fecha de fin: " + fechaFin);
        }
        
        return result;
    }
}
