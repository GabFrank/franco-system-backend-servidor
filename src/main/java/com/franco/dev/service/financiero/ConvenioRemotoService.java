package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.financiero.dto.ConvenioRemoto;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crédito por convenio de la persona en sesión en el central de la otra empresa (bodega ↔ farmacia).
 *
 * Se cruza por CI exacto: los ids de persona y cliente son de cada instancia y no coinciden. El CI no se
 * normaliza a dígitos porque un RUC con DV ({@code 1234567-8}) quedaría como el CI de otra persona.
 *
 * Solo usa queries que la versión estable ya tiene ({@code personaPorDocumento}, {@code clientePorPersonaId},
 * {@code ventaCreditoPorCliente}): así una instancia ve a la otra aunque esa siga en la versión anterior.
 * Si se cambian esas queries, esta integración se rompe. Plan:
 * docs/manuales-implementacion/financiero/plan-convenio-otra-empresa.md
 */
@Service
public class ConvenioRemotoService {

    private static final Logger log = LoggerFactory.getLogger(ConvenioRemotoService.class);

    private final UsuarioService usuarioService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String url;
    private final String token;
    private final String nombre;

    @Autowired
    public ConvenioRemotoService(UsuarioService usuarioService, ObjectMapper objectMapper,
                                 @Value("${convenio.remoto.url:}") String url,
                                 @Value("${convenio.remoto.token:}") String token,
                                 @Value("${convenio.remoto.nombre:}") String nombre) {
        this(usuarioService, objectMapper, restTemplateCorto(), url, token, nombre);
    }

    ConvenioRemotoService(UsuarioService usuarioService, ObjectMapper objectMapper, RestTemplate restTemplate,
                          String url, String token, String nombre) {
        this.usuarioService = usuarioService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.url = url;
        this.token = token;
        this.nombre = vacio(nombre) ? "la otra empresa" : nombre.trim();
    }

    /** Propio y con timeouts cortos: el bean compartido espera hasta 40 s y lo usan el proxy a filiales y la impresión. */
    private static RestTemplate restTemplateCorto() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
    }

    /**
     * {@code null} si no está configurado, si la persona en sesión no tiene CI, o si allá no existe o no es
     * cliente. Lanza si no se pudo preguntar: «no hay» y «no pude preguntar» son respuestas distintas.
     */
    public ConvenioRemoto deLaPersonaEnSesion() {
        if (vacio(url) || vacio(token)) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        Usuario usuario = usuarioService.findByNickname(auth.getName()).orElse(null);
        String documento = usuario != null && usuario.getPersona() != null ? usuario.getPersona().getDocumento() : null;
        if (vacio(documento)) return null;
        return consultar(documento.trim());
    }

    ConvenioRemoto consultar(String documento) {
        Map<String, Object> persona = graphql(
                "query($texto: String) { data: personaPorDocumento(texto: $texto) { id } }", "texto", documento);
        if (persona == null) return null;
        Map<String, Object> cliente = graphql(
                "query($id: ID) { data: clientePorPersonaId(id: $id) { id credito } }", "id", persona.get("id"));
        if (cliente == null) return null;
        List<Map<String, Object>> abiertos = graphql(
                "query($id: ID!) { data: ventaCreditoPorCliente(id: $id, estado: ABIERTO) { valorTotal } }",
                "id", cliente.get("id"));
        double utilizado = 0;
        if (abiertos != null) {
            for (Map<String, Object> c : abiertos) utilizado += numero(c.get("valorTotal"));
        }
        return new ConvenioRemoto(nombre, numero(cliente.get("credito")), utilizado);
    }

    @SuppressWarnings("unchecked")
    private <T> T graphql(String query, String variable, Object valor) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(variable, valor);
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("variables", variables);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Token " + token.trim());
        Map<String, Object> respuesta;
        try {
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            respuesta = restTemplate.postForObject(url.trim(), request, Map.class);
        } catch (Exception e) {
            // Sin el request: lleva el token en el header.
            log.warn("[CONVENIO REMOTO] No se pudo consultar {}: {}", url, e.getMessage());
            throw noSePudo();
        }
        if (respuesta == null) throw noSePudo();
        Object errores = respuesta.get("errors");
        if (errores instanceof List && !((List<?>) errores).isEmpty()) {
            log.warn("[CONVENIO REMOTO] Error GraphQL en {}: {}", url, errores);
            throw noSePudo();
        }
        Map<String, Object> data = (Map<String, Object>) respuesta.get("data");
        return data == null ? null : (T) data.get("data");
    }

    private GraphQLException noSePudo() {
        return new GraphQLException("No se pudo consultar el crédito en " + nombre);
    }

    private static double numero(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }
}
