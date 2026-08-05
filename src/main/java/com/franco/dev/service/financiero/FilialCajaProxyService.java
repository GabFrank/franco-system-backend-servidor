package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.service.empresarial.SucursalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FilialCajaProxyService {

    private static final Logger log = LoggerFactory.getLogger(FilialCajaProxyService.class);
    private static final int DEFAULT_FILIAL_PORT = 8082;

    private static final String CAJA_FIELDS =
            "id descripcion activo estado tuvoProblema fechaApertura fechaCierre observacion sucursalId creadoEn "
                    + "maletin { id descripcion } "
                    + "usuario { id persona { nombre } } "
                    + "conteoApertura { id observacion creadoEn "
                    + "  conteoMonedaList { id cantidad monedaBilletes { id valor moneda { id denominacion } } } "
                    + "} "
                    + "conteoCierre { id observacion creadoEn "
                    + "  conteoMonedaList { id cantidad monedaBilletes { id valor moneda { id denominacion } } } "
                    + "} "
                    + "balance { totalGeneral diferenciaGs diferenciaRs diferenciaDs }";

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public List<PdvCaja> cajasAbiertasPorUsuarioDesdeFiliales(Long usuarioId) {
        List<PdvCaja> result = new ArrayList<>();
        HttpHeaders headers = buildAuthHeaders();
        for (Sucursal sucursal : sucursalService.findAllExcludingServer()) {
            if (sucursal.getIp() == null || sucursal.getIp().isBlank()) {
                continue;
            }
            PdvCaja caja = consultarCajaAbiertaEnSucursal(sucursal, usuarioId, headers, "CAJAS FILIAL");
            if (caja != null) {
                result.add(caja);
            }
        }
        return result;
    }

    public PdvCaja cajaAbiertoPorUsuarioIdPorSucursal(Long usuarioId, Long sucursalId) {
        Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
        if (sucursal == null || sucursal.getIp() == null || sucursal.getIp().isBlank()) {
            return null;
        }
        return consultarCajaAbiertaEnSucursal(sucursal, usuarioId, buildAuthHeaders(), "CAJA ABIERTA FILIAL");
    }

    public PdvCaja pdvCajaDesdeFilial(Long id, Long sucursalId) {
        Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
        if (sucursal == null || sucursal.getIp() == null || sucursal.getIp().isBlank()) {
            log.warn("[PDV CAJA FILIAL] Sucursal id={} sin IP configurada", sucursalId);
            return null;
        }
        String url = buildFilialUrl(sucursal);
        String query = "query($id: ID!, $sucId: ID) { pdvCaja(id: $id, sucId: $sucId) { " + CAJA_FIELDS + " } }";
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", id);
        variables.put("sucId", sucursalId);
        try {
            Map<String, Object> data = executeGraphQL(url, buildAuthHeaders(), query, variables);
            if (data == null || data.get("pdvCaja") == null) {
                return null;
            }
            return mapToPdvCaja((Map<String, Object>) data.get("pdvCaja"), sucursal);
        } catch (Exception e) {
            log.error("[PDV CAJA FILIAL] Error al consultar caja id={} en sucursal id={}. {}",
                    id, sucursalId, e.getMessage(), e);
            return null;
        }
    }

    public Long consultarCajaAbiertaIdEnFilial(String url, HttpHeaders headers, Long usuarioId, Long sucursalId, String logPrefix) {
        String query = "query($id: ID!, $sucId: ID) { cajaAbiertoPorUsuarioId(id: $id, sucId: $sucId) { id activo } }";
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", usuarioId);
        variables.put("sucId", sucursalId);
        try {
            Map<String, Object> data = executeGraphQL(url, headers, query, variables);
            if (data == null || data.get("cajaAbiertoPorUsuarioId") == null) {
                return null;
            }
            Map<String, Object> caja = (Map<String, Object>) data.get("cajaAbiertoPorUsuarioId");
            return Long.valueOf(caja.get("id").toString());
        } catch (Exception e) {
            log.error("[{}] Excepcion al consultar caja abierta en filial. usuarioId={}. {}",
                    logPrefix, usuarioId, e.getMessage(), e);
            return null;
        }
    }

    public PdvCaja imprimirBalanceEnFilial(Long cajaId, Long sucursalId, String printerName, String local) {
        Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
        if (sucursal == null || sucursal.getIp() == null || sucursal.getIp().isBlank()) {
            log.warn("[IMPRIMIR BALANCE FILIAL] Sucursal id={} sin IP configurada", sucursalId);
            return null;
        }
        String url = buildFilialUrl(sucursal);
        String query = "query($id: ID!, $printerName: String, $local: String, $sucId: ID) { "
                + "imprimirBalance(id: $id, printerName: $printerName, local: $local, sucId: $sucId) { id } }";
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", cajaId);
        variables.put("printerName", null);
        variables.put("local", null);
        variables.put("sucId", sucursalId);
        try {
            Map<String, Object> data = executeGraphQL(url, buildAuthHeaders(), query, variables);
            if (data == null || data.get("imprimirBalance") == null) {
                return null;
            }
            PdvCaja caja = new PdvCaja();
            caja.setId(cajaId);
            caja.setSucursalId(sucursalId);
            return caja;
        } catch (Exception e) {
            log.error("[IMPRIMIR BALANCE FILIAL] Error al imprimir balance cajaId={} sucursalId={}. {}",
                    cajaId, sucursalId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Reenvia a la filial la edicion de los montos de un conteo de apertura o cierre.
     * La escritura ocurre siempre en la filial; el central recibe el cambio por replicacion.
     *
     * @return id de la nueva version del conteo creada en la filial
     */
    public Long editarConteoEnFilial(Long cajaId, Long sucursalId, Long conteoAnteriorId, Boolean apertura,
                                     Object conteoInput, List<?> conteoMonedaInputList, Long usuarioId) throws Exception {
        Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
        if (sucursal == null || sucursal.getIp() == null || sucursal.getIp().isBlank()) {
            log.error("[EDITAR CONTEO FILIAL] Sucursal id={} inexistente o sin IP configurada", sucursalId);
            throw new Exception("La sucursal no tiene IP configurada, no se puede editar el conteo");
        }
        String url = buildFilialUrl(sucursal);
        String query = "mutation($conteo: ConteoInput!, $conteoMonedaInputList: [ConteoMonedaInput], "
                + "$cajaId: Int!, $conteoAnteriorId: ID!, $apertura: Boolean!, $usuarioId: Int!) { "
                + "editarConteoCaja(conteo: $conteo, conteoMonedaInputList: $conteoMonedaInputList, "
                + "cajaId: $cajaId, conteoAnteriorId: $conteoAnteriorId, apertura: $apertura, usuarioId: $usuarioId) "
                + "{ id conteoAnteriorId } }";
        Map<String, Object> variables = new HashMap<>();
        variables.put("conteo", objectMapper.convertValue(conteoInput, Map.class));
        variables.put("conteoMonedaInputList", conteoMonedaInputList);
        variables.put("cajaId", cajaId);
        variables.put("conteoAnteriorId", conteoAnteriorId);
        variables.put("apertura", apertura);
        variables.put("usuarioId", usuarioId);

        log.info("[EDITAR CONTEO FILIAL] >>> Enviando editarConteoCaja a {} -> cajaId={}, conteoAnteriorId={}, apertura={}, usuarioId={}",
                url, cajaId, conteoAnteriorId, apertura, usuarioId);
        Map<String, Object> data = executeGraphQLOrThrow(url, buildAuthHeaders(), query, variables);
        Map<String, Object> conteo = data != null ? (Map<String, Object>) data.get("editarConteoCaja") : null;
        if (conteo == null || conteo.get("id") == null) {
            throw new Exception("La filial no devolvio el conteo editado");
        }
        Long nuevoConteoId = Long.valueOf(conteo.get("id").toString());
        log.info("[EDITAR CONTEO FILIAL] <<< Conteo editado en filial -> nuevoConteoId={}, reemplaza a {}",
                nuevoConteoId, conteoAnteriorId);
        return nuevoConteoId;
    }

    public String buildFilialUrl(Sucursal sucursal) {
        Integer puerto = sucursal.getPuertoServidor() != null ? sucursal.getPuertoServidor() : DEFAULT_FILIAL_PORT;
        return "http://" + sucursal.getIp() + ":" + puerto + "/graphql";
    }

    public HttpHeaders buildAuthHeaders() {
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

    private PdvCaja consultarCajaAbiertaEnSucursal(Sucursal sucursal, Long usuarioId, HttpHeaders headers, String logPrefix) {
        String url = buildFilialUrl(sucursal);
        String query = "query($id: ID!, $sucId: ID) { cajaAbiertoPorUsuarioId(id: $id, sucId: $sucId) { " + CAJA_FIELDS + " } }";
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", usuarioId);
        variables.put("sucId", sucursal.getId());
        try {
            Map<String, Object> data = executeGraphQL(url, headers, query, variables);
            if (data == null || data.get("cajaAbiertoPorUsuarioId") == null) {
                return null;
            }
            return mapToPdvCaja((Map<String, Object>) data.get("cajaAbiertoPorUsuarioId"), sucursal);
        } catch (Exception e) {
            log.warn("[{}] No se pudo consultar filial {} ({}): {}",
                    logPrefix, sucursal.getNombre(), url, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> executeGraphQL(String url, HttpHeaders headers, String query, Map<String, Object> variables) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("variables", variables);
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        if (response.getBody() == null || response.getBody().containsKey("errors")) {
            log.warn("Error GraphQL en filial {}: {}", url, response.getBody());
            return null;
        }
        return (Map<String, Object>) response.getBody().get("data");
    }

    /**
     * Igual que {@link #executeGraphQL} pero propaga el mensaje de error de la filial en vez de
     * devolver null. Se usa en operaciones donde el motivo del rechazo ("la caja ya fue verificada",
     * "el conteo fue editado por otro usuario") tiene que llegar al usuario.
     */
    private Map<String, Object> executeGraphQLOrThrow(String url, HttpHeaders headers, String query,
                                                      Map<String, Object> variables) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("variables", variables);
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        if (response.getBody() == null) {
            throw new Exception("La filial no devolvio respuesta");
        }
        Object errors = response.getBody().get("errors");
        if (errors instanceof List && !((List<?>) errors).isEmpty()) {
            Object primerError = ((List<?>) errors).get(0);
            String mensaje = primerError instanceof Map && ((Map<?, ?>) primerError).get("message") != null
                    ? ((Map<?, ?>) primerError).get("message").toString()
                    : errors.toString();
            log.warn("Error GraphQL en filial {}: {}", url, mensaje);
            throw new Exception(mensaje);
        }
        return (Map<String, Object>) response.getBody().get("data");
    }

    private PdvCaja mapToPdvCaja(Map<String, Object> cajaMap, Sucursal sucursal) {
        PdvCaja caja = objectMapper.convertValue(cajaMap, PdvCaja.class);
        caja.setSucursalId(sucursal.getId());
        return caja;
    }
}
