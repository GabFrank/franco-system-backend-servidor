package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.dto.ErrorResponseDTO;
import com.franco.dev.service.financiero.dto.FacturaLegalFilialRequest;
import com.franco.dev.service.financiero.dto.FacturaLegalFilialResponse;
import com.franco.dev.service.financiero.dto.FacturaLegalItemFilialRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FacturaLegalFilialService {

    private static final Logger log = LoggerFactory.getLogger(FacturaLegalFilialService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ObjectMapper objectMapper;

    public FacturaLegalFilialResponse crearFacturaLegalEnFilial(
            FacturaLegalInput facturaInput,
            List<FacturaLegalItemInput> items,
            Long sucursalId,
            Long timbradoDetalleId
    ) throws RestClientException {
        Sucursal sucursal = sucursalService.findById(sucursalId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada: " + sucursalId));

        if (sucursal.getIp() == null) {
            throw new RuntimeException("La sucursal no tiene IP configurada");
        }

        Integer puertoServidor = sucursal.getPuertoServidor() != null ? sucursal.getPuertoServidor() : 8082;
        String url = String.format("http://%s:%d/api/facturas-legales", sucursal.getIp(), puertoServidor);

        FacturaLegalFilialRequest request = mapearRequest(facturaInput, items, timbradoDetalleId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Serializar manualmente el request a JSON para asegurar formato correcto
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);
            log.debug("Request JSON serializado: {}", requestJson);
            
            // Verificar que la fecha esté en formato string, no array
            if (requestJson.contains("\"fecha\" : [") || requestJson.contains("\"fecha\":[")) {
                log.error("ERROR: La fecha se está serializando como array. Request: {}", requestJson);
                throw new RuntimeException("Error en serialización de fecha: se está enviando como array en lugar de string");
            }
        } catch (Exception e) {
            log.error("Error al serializar el request: {}", e.getMessage(), e);
            throw new RuntimeException("Error al preparar la petición al servidor filial: " + e.getMessage(), e);
        }

        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        log.info("Enviando factura legal a servidor filial: {} - URL: {}", sucursal.getNombre(), url);

        try {
            ResponseEntity<FacturaLegalFilialResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    FacturaLegalFilialResponse.class
            );

            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                log.info("Factura legal creada exitosamente en servidor filial. ID: {}", response.getBody().getId());
                return response.getBody();
            } else {
                String mensajeError = "Error al crear factura en servidor filial. Código: " + response.getStatusCode();
                log.error(mensajeError);
                throw new RuntimeException(mensajeError);
            }
            
        } catch (HttpClientErrorException e) {
            // Error 4xx (400, 404, etc.)
            ErrorResponseDTO errorResponse = parseErrorResponse(e.getResponseBodyAsString());
            String mensajeError = errorResponse != null ? errorResponse.getMensaje() : e.getMessage();
            log.error("Error al crear factura legal en servidor filial ({}): {}", e.getStatusCode(), mensajeError);
            throw new RuntimeException("Error al crear factura legal: " + mensajeError, e);
            
        } catch (HttpServerErrorException e) {
            // Error 5xx (500, 503, etc.)
            ErrorResponseDTO errorResponse = parseErrorResponse(e.getResponseBodyAsString());
            String mensajeError = errorResponse != null ? errorResponse.getMensaje() : e.getMessage();
            log.error("Error del servidor filial ({}): {}", e.getStatusCode(), mensajeError);
            throw new RuntimeException("Error del servidor filial: " + mensajeError, e);
            
        } catch (RestClientException e) {
            // Error de conexión u otro error de RestTemplate
            log.error("Error al conectar con el servidor filial: {}", e.getMessage(), e);
            throw new RuntimeException("Error al conectar con el servidor filial: " + e.getMessage(), e);
        }
    }

    private FacturaLegalFilialRequest mapearRequest(
            FacturaLegalInput facturaInput,
            List<FacturaLegalItemInput> items,
            Long timbradoDetalleId
    ) {
        FacturaLegalFilialRequest request = new FacturaLegalFilialRequest();
        request.setTimbradoDetalleId(timbradoDetalleId);
        request.setCajaId(facturaInput.getCajaId() != null ? facturaInput.getCajaId().longValue() : null);
        request.setClienteId(facturaInput.getClienteId() != null ? facturaInput.getClienteId().longValue() : null);
        request.setNombre(facturaInput.getNombre());
        request.setRuc(facturaInput.getRuc());
        request.setDireccion(facturaInput.getDireccion());
        request.setEmail(facturaInput.getEmail());
        request.setViaTributaria(facturaInput.getViaTributaria());
        request.setCredito(facturaInput.getCredito());
        
        // Formatear fecha usando formato ISO consistente (sin zona horaria)
        // Usar el mismo formato que se usa en el resto del sistema para evitar diferencias de horario
        String fechaFormateada;
        if (facturaInput.getFecha() != null) {
            // Formatear LocalDateTime a String ISO
            fechaFormateada = facturaInput.getFecha().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } else {
            // Si no hay fecha, usar la fecha/hora actual sin zona horaria
            fechaFormateada = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        }
        request.setFecha(fechaFormateada);
        log.debug("Fecha formateada para envío: {}", fechaFormateada);
        
        // Mapear items y calcular totales de IVA
        List<FacturaLegalItemFilialRequest> itemsRequest = items.stream()
                .map(item -> {
                    FacturaLegalItemFilialRequest itemRequest = new FacturaLegalItemFilialRequest();
                    itemRequest.setProductoId(item.getProductoId() != null ? item.getProductoId().longValue() : null);
                    itemRequest.setDescripcion(item.getDescripcion());
                    itemRequest.setCantidad(item.getCantidad() != null ? item.getCantidad().doubleValue() : null);
                    itemRequest.setPrecioUnitario(item.getPrecioUnitario() != null ? item.getPrecioUnitario().doubleValue() : null);
                    
                    // Calcular total del item si no viene
                    Double totalItem = item.getTotal() != null ? item.getTotal().doubleValue() : null;
                    if (totalItem == null && itemRequest.getCantidad() != null && itemRequest.getPrecioUnitario() != null) {
                        totalItem = itemRequest.getCantidad() * itemRequest.getPrecioUnitario();
                    }
                    itemRequest.setTotal(totalItem);
                    
                    itemRequest.setIva(item.getIva() != null ? item.getIva().intValue() : null);
                    
                    // Normalizar unidad de medida a mayúsculas
                    String unidadMedida = item.getUnidadMedida();
                    if (unidadMedida != null && !unidadMedida.isEmpty()) {
                        itemRequest.setUnidadMedida(unidadMedida.toUpperCase().trim());
                    } else {
                        itemRequest.setUnidadMedida("UNIDAD");
                    }
                    
                    return itemRequest;
                })
                .collect(Collectors.toList());
        request.setItems(itemsRequest);
        
        // Calcular parciales con descuento distribuido proporcional usando
        // ParcialesCalculator centralizado. Fix del bug: el codigo viejo restaba
        // el descuento del total_final sin distribuirlo a los parciales,
        // generando sum(parciales) > total_final.
        java.util.List<com.franco.dev.service.financiero.builder.ParcialesCalculator.ItemIvaTuple> tuples =
                new java.util.ArrayList<>();
        for (FacturaLegalItemFilialRequest item : itemsRequest) {
            if (item.getTotal() == null || item.getIva() == null) {
                continue;
            }
            tuples.add(new com.franco.dev.service.financiero.builder.ParcialesCalculator.ItemIvaTuple(
                    item.getIva(), item.getTotal()));
        }
        Double descuento = facturaInput.getDescuento() != null ? facturaInput.getDescuento().doubleValue() : 0.0;
        com.franco.dev.service.financiero.builder.ParcialesCalculator.Resultado calc =
                com.franco.dev.service.financiero.builder.ParcialesCalculator.calcular(tuples, descuento);

        // Redondear los valores de IVA a 2 decimales
        double ivaParcial5 = Math.round(calc.getIvaParcial5() * 100.0) / 100.0;
        double ivaParcial10 = Math.round(calc.getIvaParcial10() * 100.0) / 100.0;

        request.setIvaParcial0(0.0);
        request.setIvaParcial5(ivaParcial5);
        request.setIvaParcial10(ivaParcial10);
        request.setTotalParcial0(calc.getTotalParcial0());
        request.setTotalParcial5(calc.getTotalParcial5());
        request.setTotalParcial10(calc.getTotalParcial10());

        double totalFinalCalculado = calc.getTotalFinal();
        Double totalFinalInput = facturaInput.getTotalFinal() != null ? facturaInput.getTotalFinal().doubleValue() : null;
        if (totalFinalInput != null) {
            double diferencia = Math.abs(totalFinalInput - totalFinalCalculado);
            if (diferencia > 0.01) {
                log.warn("Diferencia entre totalFinal calculado ({}) e input ({}): {}. Se usará el calculado.",
                        totalFinalCalculado, totalFinalInput, diferencia);
            }
        }

        request.setTotalFinal(totalFinalCalculado);
        request.setDescuento(descuento);
        
        // Mapear moneda extranjera a código SIFEN (ISO 4217)
        String monedaExtranjera = facturaInput.getMonedaExtranjera();
        if (monedaExtranjera != null && !monedaExtranjera.isEmpty()) {
            String codigoSifen = mapearMonedaACodigoSifen(monedaExtranjera);
            request.setMonedaExtranjera(codigoSifen);
        } else {
            request.setMonedaExtranjera(null);
        }
        
        request.setTipoCambio(facturaInput.getTipoCambio() != null ? facturaInput.getTipoCambio().doubleValue() : null);
        
        return request;
    }

    /**
     * Mapea la denominación de moneda a su código SIFEN (ISO 4217).
     * 
     * @param denominacion La denominación de la moneda (ej: "DOLAR", "REAL", "PESO ARG")
     * @return El código SIFEN correspondiente (ej: "USD", "BRL", "ARS"), o la denominación original si no se encuentra mapeo
     */
    private String mapearMonedaACodigoSifen(String denominacion) {
        if (denominacion == null || denominacion.isEmpty()) {
            return null;
        }

        // Normalizar la denominación (mayúsculas, sin espacios extra)
        String denominacionNormalizada = denominacion.toUpperCase().trim();

        // Mapeo de denominaciones comunes a códigos SIFEN/ISO 4217
        switch (denominacionNormalizada) {
            case "DOLAR":
            case "DÓLAR":
            case "USD":
                return "USD";
            case "REAL":
            case "BRL":
                return "BRL";
            case "PESO ARG":
            case "PESO ARGENTINO":
            case "ARS":
                return "ARS";
            case "EURO":
            case "EUR":
                return "EUR";
            case "GUARANI":
            case "PYG":
                return "PYG";
            default:
                // Si ya es un código de 3 letras, asumir que es válido
                if (denominacionNormalizada.length() == 3) {
                    log.debug("Usando denominación como código SIFEN directamente: {}", denominacionNormalizada);
                    return denominacionNormalizada;
                }
                // Si no se encuentra mapeo, loguear advertencia y retornar la denominación original
                log.warn("No se encontró mapeo para la moneda '{}', se usará la denominación original", denominacion);
                return denominacionNormalizada;
        }
    }

    /**
     * Parsea el cuerpo de la respuesta de error del servidor filial.
     * Soporta tanto JSON como XML.
     * Si el cuerpo no puede ser parseado, retorna null.
     *
     * @param responseBody El cuerpo de la respuesta HTTP como String
     * @return ErrorResponseDTO con el error parseado, o null si no se puede parsear
     */
    private ErrorResponseDTO parseErrorResponse(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }

        // Intentar parsear como JSON primero
        try {
            return objectMapper.readValue(responseBody, ErrorResponseDTO.class);
        } catch (Exception e) {
            // Si falla, intentar parsear como XML
            return parseErrorResponseFromXml(responseBody);
        }
    }

    /**
     * Parsea una respuesta de error en formato XML.
     * Extrae los valores de los tags: mensaje, error, codigo
     *
     * @param xmlBody El cuerpo de la respuesta HTTP como XML String
     * @return ErrorResponseDTO con el error parseado, o null si no se puede parsear
     */
    private ErrorResponseDTO parseErrorResponseFromXml(String xmlBody) {
        try {
            String mensaje = extractXmlTagValue(xmlBody, "mensaje");
            String error = extractXmlTagValue(xmlBody, "error");
            String codigoStr = extractXmlTagValue(xmlBody, "codigo");

            if (mensaje == null && error == null && codigoStr == null) {
                // No se encontraron los tags esperados
                return null;
            }

            ErrorResponseDTO errorResponse = new ErrorResponseDTO();
            errorResponse.setMensaje(mensaje);
            errorResponse.setError(error);

            if (codigoStr != null) {
                try {
                    errorResponse.setCodigo(Integer.parseInt(codigoStr));
                } catch (NumberFormatException e) {
                    log.warn("No se pudo parsear el código de error como número: {}", codigoStr);
                }
            }

            return errorResponse;
        } catch (Exception e) {
            log.warn("No se pudo parsear la respuesta de error XML del servidor filial: {}", xmlBody);
            return null;
        }
    }

    /**
     * Extrae el valor de un tag XML simple.
     *
     * @param xml El XML completo como String
     * @param tagName El nombre del tag a buscar (sin < >)
     * @return El valor del tag, o null si no se encuentra
     */
    private String extractXmlTagValue(String xml, String tagName) {
        if (xml == null || xml.isEmpty() || tagName == null || tagName.isEmpty()) {
            return null;
        }

        try {
            String openTag = "<" + tagName + ">";
            String closeTag = "</" + tagName + ">";

            int startIndex = xml.indexOf(openTag);
            int endIndex = xml.indexOf(closeTag);

            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                return xml.substring(startIndex + openTag.length(), endIndex).trim();
            }
        } catch (Exception e) {
            log.debug("Error al extraer tag XML '{}': {}", tagName, e.getMessage());
        }

        return null;
    }
}

