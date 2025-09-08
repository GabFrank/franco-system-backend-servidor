package com.franco.dev.service.financiero;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import com.franco.dev.domain.financiero.DocumentoElectronico;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DteNodeClient {

    private final RestTemplate restTemplate;

    @Value("${dte.node.base-url:http://localhost:3000}")
    private String baseUrl;

    @Value("${dte.node.endpoints.generar:/api/documento/generar}")
    private String generarEndpoint;

    @Value("${dte.node.endpoints.enviar-lote:/api/lote/enviar}")
    private String enviarLoteEndpoint;

    @Value("${dte.node.endpoints.consultar-lote:/api/lote/{id}}")
    private String consultarLoteEndpoint;

    @Value("${dte.node.endpoints.registrar-evento:/api/evento/registrar}")
    private String registrarEventoEndpoint;


    @Value("${dte.node.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${dte.node.max-retries:3}")
    private int maxRetries;

    @Value("${dte.node.backoff-ms:1000}")
    private int backoffMs;

    // Getters para logging
    public String getBaseUrl() { return baseUrl; }
    public String getGenerarEndpoint() { return generarEndpoint; }

    public GenerarDocumentoResponse generarDocumentoDesdeFactura(Long facturaId, Long sucursalId) {
        System.out.println("🚀🚀🚀 DTE NodeClient: MÉTODO generarDocumentoDesdeFactura EJECUTÁNDOSE");
        System.out.println("🚀🚀🚀 DTE NodeClient: facturaId=" + facturaId + ", sucursalId=" + sucursalId);
        System.out.println("🚀🚀🚀 DTE NodeClient: LLAMANDO AL MICROSERVICIO");
        String url = baseUrl + generarEndpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Crear el body con el formato que espera el microservicio Node.js
        Map<String, Object> body = new HashMap<>();
        body.put("facturaId", facturaId);
        body.put("sucursalId", sucursalId);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        // Logging para debug
        System.out.println("🚀🚀🚀 DTE NodeClient: Enviando POST a URL: " + url);
        System.out.println("🚀🚀🚀 DTE NodeClient: Body enviado: " + body);
        
        try {
            System.out.println("🚀🚀🚀 DTE NodeClient: Iniciando llamada HTTP al microservicio...");
            
            // Usar la nueva clase wrapper que coincide con la estructura JSON del microservicio
            MicroservicioResponse microservicioResponse = executeWithRetry(() -> restTemplate.postForObject(url, entity, MicroservicioResponse.class));
            
            System.out.println("🚀🚀🚀 DTE NodeClient: Respuesta HTTP recibida del microservicio: " + microservicioResponse);
            
            if (microservicioResponse != null && microservicioResponse.getDte() != null) {
                DteData dteData = microservicioResponse.getDte();
                System.out.println("🚀🚀🚀 DTE NodeClient: DTE data extraída: " + dteData);
                System.out.println("🚀🚀🚀 DTE NodeClient: CDC en DTE: '" + dteData.getCdc() + "'");
                System.out.println("🚀🚀🚀 DTE NodeClient: XML en DTE: " + (dteData.getXml() != null ? dteData.getXml().length() + " caracteres" : "null"));
                System.out.println("🚀🚀🚀 DTE NodeClient: QR en DTE: '" + dteData.getQrUrl() + "'");
                
                // Convertir a GenerarDocumentoResponse
                GenerarDocumentoResponse response = new GenerarDocumentoResponse();
                response.setCdc(dteData.getCdc());
                response.setXmlFirmado(dteData.getXml());
                response.setUrlQr(dteData.getQrUrl());
                return response;
            } else {
                System.err.println("❌❌❌ DTE NodeClient: Respuesta del microservicio es null o no tiene DTE");
                return null;
            }
        } catch (Exception e) {
            System.err.println("❌❌❌ DTE NodeClient: Error en POST: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public String enviarLote(List<DocumentoElectronico> documentos) {
        System.err.println("🚀🚀🚀 DTE NodeClient: MÉTODO enviarLote EJECUTÁNDOSE");
        System.err.println("🚀🚀🚀 DTE NodeClient: Cantidad de documentos: " + documentos.size());
        
        String url = baseUrl + enviarLoteEndpoint;
        System.err.println("🚀🚀🚀 DTE NodeClient: URL del endpoint: " + url);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        System.err.println("🚀🚀🚀 DTE NodeClient: Headers configurados: " + headers);
        
        // Crear el body con XML y CDC para cada DTE
        List<Map<String, String>> dtesData = documentos.stream()
            .map(doc -> {
                Map<String, String> dteData = new HashMap<>();
                dteData.put("xml", doc.getXmlFirmado());
                dteData.put("cdc", doc.getCdc());
                
                System.err.println("🚀🚀🚀 DTE NodeClient: DTE procesado - CDC: " + doc.getCdc() + 
                                 ", XML length: " + (doc.getXmlFirmado() != null ? doc.getXmlFirmado().length() : "NULL"));
                
                return dteData;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> body = new HashMap<>();
        body.put("dtes", dtesData);
        
        System.err.println("🚀🚀🚀 DTE NodeClient: Body preparado: " + body);
        System.err.println("🚀🚀🚀 DTE NodeClient: Cantidad de DTEs en body: " + dtesData.size());
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        System.err.println("🚀🚀🚀 DTE NodeClient: Entity creada, iniciando llamada HTTP...");
        
        try {
            EnviarLoteResponse res = executeWithRetry(() -> restTemplate.postForObject(url, entity, EnviarLoteResponse.class));
            System.err.println("🚀🚀🚀 DTE NodeClient: Respuesta recibida: " + res);
            return res != null ? res.getIdProtocolo() : null;
        } catch (Exception e) {
            System.err.println("❌❌❌ DTE NodeClient: Error en enviarLote: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public String consultarLote(String protocoloId) {
        String url = baseUrl + consultarLoteEndpoint.replace("{id}", protocoloId);
        ConsultarLoteResponse res = executeWithRetry(() -> restTemplate.getForObject(url, ConsultarLoteResponse.class));
        return res != null ? res.getRespuesta() : null;
    }

    public RegistrarEventoResponse registrarEvento(String cdcDocumento, Integer tipoEvento, String motivo, String observacion) {
        String url = baseUrl + registrarEventoEndpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Crear el body con el formato que espera el microservicio Node.js
        Map<String, Object> body = new HashMap<>();
        body.put("cdc", cdcDocumento);
        body.put("tipoEvento", tipoEvento);
        body.put("motivo", motivo);
        body.put("observacion", observacion);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return executeWithRetry(() -> restTemplate.postForObject(url, entity, RegistrarEventoResponse.class));
    }

    /**
     * Consulta el estado individual de un documento en SIFEN
     * @param cdc Código de Control del documento
     * @return Estado del documento
     */
    public String consultarEstadoIndividual(String cdc) {
        System.out.println("🚀🚀🚀 DTE NodeClient: MÉTODO consultarEstadoIndividual EJECUTÁNDOSE");
        System.out.println("🚀🚀🚀 DTE NodeClient: cdc=" + cdc);

        try {
            System.out.println("🚀🚀🚀 DTE NodeClient: LLAMANDO AL MICROSERVICIO");
            
            String url = baseUrl + "/api/documento/" + cdc + "/estado";
            System.out.println("🚀🚀🚀 DTE NodeClient: Enviando GET a URL: " + url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            System.out.println("🚀🚀🚀 DTE NodeClient: Iniciando llamada HTTP al microservicio...");
            
            // Usar la nueva clase wrapper que coincide con la estructura JSON del microservicio
            ResponseEntity<Map> responseEntity = executeWithRetry(() -> restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                entity,
                Map.class
            ));
            
            Map response = responseEntity != null ? responseEntity.getBody() : null;
            
            System.out.println("🚀🚀🚀 DTE NodeClient: Respuesta HTTP recibida del microservicio: " + response);
            
            if (response != null && response.containsKey("estado")) {
                String estado = (String) response.get("estado");
                System.out.println("🚀🚀🚀 DTE NodeClient: Estado extraído: '" + estado + "'");
                return estado;
            } else {
                System.err.println("❌❌❌ DTE NodeClient: Respuesta no contiene estado, retornando PENDIENTE");
                return "PENDIENTE";
            }
            
        } catch (Exception e) {
            System.err.println("❌❌❌ DTE NodeClient: Error consultando estado individual del documento " + cdc + ": " + e.getMessage());
            e.printStackTrace();
            return "ERROR_CONSULTA";
        }
    }

    private <T> T executeWithRetry(SupplierWithException<T> supplier) {
        int attempts = 0;
        Exception lastEx = null;
        while (attempts < maxRetries) {
            try {
                return supplier.get();
            } catch (ResourceAccessException e) {
                lastEx = e;
                try {
                    long delay = backoffMs * (long) Math.pow(2, attempts); // Backoff exponencial
                    System.out.println("🚀🚀🚀 DTE NodeClient: ResourceAccessException - Reintentando en " + delay + "ms (intento " + (attempts + 1) + "/" + maxRetries + ")");
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {}
                attempts++;
            } catch (HttpClientErrorException e) {
                lastEx = e;
                // Si es 429 Too Many Requests, usar backoff exponencial más agresivo
                if (e.getStatusCode().value() == 429) {
                    try {
                        long delay = backoffMs * (long) Math.pow(3, attempts); // Backoff exponencial más agresivo para 429
                        System.out.println("🚀🚀🚀 DTE NodeClient: 429 Too Many Requests - Reintentando en " + delay + "ms (intento " + (attempts + 1) + "/" + maxRetries + ")");
                        Thread.sleep(delay);
                    } catch (InterruptedException ignored) {}
                    attempts++;
                } else {
                    // Para otros errores HTTP client (4xx), no reintentar
                    System.err.println("❌❌❌ DTE NodeClient: Error HTTP client no reintentable: " + e.getStatusCode() + " - " + e.getMessage());
                    throw e;
                }
            } catch (Exception e) {
                lastEx = e;
                // Para otros errores generales, reintentar con backoff normal
                try {
                    long delay = backoffMs * (attempts + 1);
                    System.err.println("❌❌❌ DTE NodeClient: Error general - Reintentando en " + delay + "ms (intento " + (attempts + 1) + "/" + maxRetries + "): " + e.getMessage());
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {}
                attempts++;
            }
        }
        if (lastEx != null) {
            System.err.println("❌❌❌ DTE NodeClient: Máximo de reintentos alcanzado. Último error: " + lastEx.getMessage());
            throw lastEx instanceof RuntimeException ? (RuntimeException) lastEx : new RuntimeException(lastEx);
        }
        return null;
    }

    @FunctionalInterface
    interface SupplierWithException<T> {
        T get() throws Exception;
    }

    @Data
    public static class GenerarDocumentoRequest {
        private Long facturaLegalId;
        private Long sucursalId;
    }

    @Data
    public static class GenerarDocumentoResponse {
        private String cdc;
        private String xmlFirmado;
        private String urlQr;
    }

    // Clase wrapper para la respuesta del microservicio
    @Data
    public static class MicroservicioResponse {
        private boolean success;
        private DteData dte;
    }

    @Data
    public static class DteData {
        private Long id;
        private String cdc;
        private String xml;
        private String qrUrl;
        private String estado;
        private String fechaGeneracion;
    }

    @Data
    public static class EnviarLoteResponse {
        private String idProtocolo;
    }

    @Data
    public static class ConsultarLoteResponse {
        private String respuesta;
    }

    @Data
    public static class RegistrarEventoResponse {
        private String cdcEvento;
        private String mensaje;
    }
}


