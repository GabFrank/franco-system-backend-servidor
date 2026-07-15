package com.franco.dev.service.impresion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.ImpresoraService;
import com.franco.dev.utilitarios.print.TicketFormato;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Router de impresion (Fase 3). Decide si una impresion se ejecuta localmente en este
 * host o se reenvia por GraphQL al host donde esta conectada la impresora.
 *
 * Regla: cada {@link Impresora} pertenece a una sucursal ({@code impresora.sucursal}).
 * Si esa sucursal es la de este host ({@code sucursalId} de application.properties) se
 * imprime local; si no, se hace proxy a la filial dueña (puerto 8082).
 *
 * En el servidor central ({@code sucursalId=0}) el proxy solo aplica hacia filiales.
 */
@Service
public class PrintRouterService {

    private static final Logger log = LoggerFactory.getLogger(PrintRouterService.class);
    private static final int DEFAULT_FILIAL_PORT = 8082;

    @Value("${sucursalId:0}")
    private Long miSucursalId;

    @Autowired
    private ImpresoraService impresoraService;

    @Autowired
    private ImpresoraPrintService impresoraPrintService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Punto de entrada con routing: imprime local si la impresora es de este host, o
     * reenvia al host dueño.
     */
    public Boolean imprimir(Long impresoraId, byte[] payload) {
        Impresora impresora = impresoraService.findById(impresoraId).orElse(null);
        if (impresora == null) {
            log.warn("[PRINT] Impresora id={} no encontrada", impresoraId);
            return false;
        }
        Long ownerId = impresora.getSucursal() != null ? impresora.getSucursal().getId() : miSucursalId;
        if (ownerId.equals(miSucursalId)) {
            return imprimirLocal(impresora, payload);
        }
        return proxyImprimir(impresora.getSucursal(), impresoraId, payload);
    }

    /**
     * Ejecuta la impresion LOCAL en este host (hoja del routing; no reenvia).
     */
    public Boolean imprimirLocal(Impresora impresora, byte[] payload) {
        try (OutputStream out = impresoraPrintService.abrirOutput(impresora)) {
            out.write(payload);
            out.flush();
            return true;
        } catch (Exception e) {
            log.error("[PRINT] Error imprimiendo local en impresora id={}: {}",
                    impresora.getId(), e.getMessage(), e);
            return false;
        }
    }

    public Boolean imprimirLocalPorId(Long impresoraId, byte[] payload) {
        Impresora impresora = impresoraService.findById(impresoraId).orElse(null);
        if (impresora == null) {
            log.warn("[PRINT] Impresora id={} no encontrada (local)", impresoraId);
            return false;
        }
        return imprimirLocal(impresora, payload);
    }

    /**
     * Genera un ticket ESC/POS de prueba respetando el ancho (columnas) de la impresora.
     */
    public byte[] generarTicketPrueba(Impresora impresora) {
        TicketFormato f = impresoraPrintService.formato(impresora);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            EscPos escpos = new EscPos(baos);
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            escpos.writeLF(center.setBold(true), "PRUEBA DE IMPRESION");
            escpos.writeLF(f.separador());
            escpos.writeLF("Impresora: " + safe(impresora.getNombre()));
            if (impresora.getSucursal() != null) {
                escpos.writeLF("Sucursal: " + safe(impresora.getSucursal().getNombre()));
            }
            escpos.writeLF("Conexion: " + impresora.getConexion());
            escpos.writeLF(f.izqDer("Columnas:", String.valueOf(f.getColumnas())));
            escpos.writeLF(f.izqDer("Perfil:", String.valueOf(impresora.getPerfilPapel())));
            escpos.writeLF(f.separador());
            escpos.feed(3);
            escpos.cut(EscPos.CutMode.FULL);
        } catch (Exception e) {
            log.error("[PRINT] Error generando ticket de prueba: {}", e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    // ---- proxy hacia el host dueño ----

    private Boolean proxyImprimir(Sucursal owner, Long impresoraId, byte[] payload) {
        if (owner == null || owner.getIp() == null || owner.getIp().isBlank()) {
            log.warn("[PRINT] Sucursal dueña sin IP para impresora id={}", impresoraId);
            return false;
        }
        Integer puerto = owner.getPuertoServidor() != null ? owner.getPuertoServidor() : DEFAULT_FILIAL_PORT;
        String url = "http://" + owner.getIp() + ":" + puerto + "/graphql";
        String payloadBase64 = Base64.getEncoder().encodeToString(payload);
        String query = "mutation($id: ID!, $payload: String!) { "
                + "imprimirEnDestino(impresoraId: $id, payloadBase64: $payload) }";
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", impresoraId);
        variables.put("payload", payloadBase64);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("variables", variables);
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), buildAuthHeaders());
            restTemplate.postForEntity(url, request, Map.class);
            log.info("[PRINT] Reenviado a {} (impresora id={})", url, impresoraId);
            return true;
        } catch (Exception e) {
            log.error("[PRINT] Error reenviando impresion a {}: {}", url, e.getMessage(), e);
            return false;
        }
    }

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest currentRequest = attributes.getRequest();
            String authHeader = currentRequest.getHeader("Authorization");
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }
        }
        return headers;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
