package com.franco.dev.service.financiero;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Value("${dte.node.mock:true}")
    private boolean mock;

    @Value("${dte.node.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${dte.node.max-retries:3}")
    private int maxRetries;

    @Value("${dte.node.backoff-ms:1000}")
    private int backoffMs;

    public GenerarDocumentoResponse generarDocumentoDesdeFactura(Long facturaId, Long sucursalId) {
        if (mock) {
            GenerarDocumentoResponse m = new GenerarDocumentoResponse();
            m.setCdc(String.format("%044d", System.nanoTime() % 1_000_000_000));
            m.setXmlFirmado("<xml>mock</xml>");
            m.setUrlQr("https://kude.mock/" + facturaId);
            return m;
        }
        String url = baseUrl + generarEndpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Crear el body con el formato que espera el microservicio Node.js
        Map<String, Object> body = new HashMap<>();
        body.put("facturaId", facturaId);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return executeWithRetry(() -> restTemplate.postForObject(url, entity, GenerarDocumentoResponse.class));
    }

    public String enviarLote(List<String> xmlFirmados) {
        if (mock) {
            return "mock-protocolo-" + System.currentTimeMillis();
        }
        String url = baseUrl + enviarLoteEndpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Crear el body con el formato que espera el microservicio Node.js
        Map<String, Object> body = new HashMap<>();
        body.put("dtes", xmlFirmados);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        EnviarLoteResponse res = executeWithRetry(() -> restTemplate.postForObject(url, entity, EnviarLoteResponse.class));
        return res != null ? res.getIdProtocolo() : null;
    }

    public String consultarLote(String protocoloId) {
        if (mock) {
            return "<respuesta><aprobado cdc='MOCK'/></respuesta>";
        }
        String url = baseUrl + consultarLoteEndpoint.replace("{id}", protocoloId);
        ConsultarLoteResponse res = executeWithRetry(() -> restTemplate.getForObject(url, ConsultarLoteResponse.class));
        return res != null ? res.getRespuesta() : null;
    }

    public RegistrarEventoResponse registrarEvento(String cdcDocumento, Integer tipoEvento, String motivo, String observacion) {
        if (mock) {
            RegistrarEventoResponse m = new RegistrarEventoResponse();
            m.setCdcEvento("MOCK-EVT-" + System.currentTimeMillis());
            m.setMensaje("Evento registrado (mock)");
            return m;
        }
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

    private <T> T executeWithRetry(SupplierWithException<T> supplier) {
        int attempts = 0;
        ResourceAccessException lastEx = null;
        while (attempts < maxRetries) {
            try {
                return supplier.get();
            } catch (ResourceAccessException e) {
                lastEx = e;
                try { Thread.sleep(backoffMs * (attempts + 1)); } catch (InterruptedException ignored) {}
                attempts++;
            }
        }
        if (lastEx != null) throw lastEx;
        return null;
    }

    @FunctionalInterface
    interface SupplierWithException<T> {
        T get() throws ResourceAccessException;
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


