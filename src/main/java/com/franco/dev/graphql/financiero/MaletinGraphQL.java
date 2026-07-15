package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.graphql.financiero.input.MaletinInput;
import com.franco.dev.service.financiero.MaletinService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.SucursalService;

@Component
public class MaletinGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MaletinService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(MaletinGraphQL.class);

    public Optional<Maletin> maletin(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<Maletin> searchMaletin(String texto, Long sucId) {
        if (texto == null || texto.isEmpty()) {
            return service.findBySucursalIdOrAll(sucId);
        }
        return service.searchByAll(texto, sucId);
    }

    public List<Maletin> maletines(int page, int size) {
        return service.findAll2();
    }

    public Maletin saveMaletin(MaletinInput input) {
        ModelMapper m = new ModelMapper();
        Maletin e = m.map(input, Maletin.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        // return multiTenantService.compartir("filial"+input.getSucursalId(), (params)
        // -> service.save(e), e);
        return service.save(e);
    }

    public Maletin maletinPorDescripcion(String texto) {
        return service.findByDescripcion(texto);
    }

    public Maletin maletinPorDescripcionPorSucursal(String texto, Long sucId) {
        try {
            Sucursal sucursal = sucursalService.findById(sucId).orElse(null);
            if (sucursal == null || sucursal.getIp() == null) {
                log.error("Sucursal no encontrada o sin IP configurado.");
                return null;
            }
            Integer puerto = sucursal.getPuertoServidor() != null ? sucursal.getPuertoServidor() : 8082;
            String url = "http://" + sucursal.getIp() + ":" + puerto + "/graphql";

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

            String query = "query($texto: String!) { maletinPorDescripcion(texto: $texto) { id descripcion activo abierto } }";
            Map<String, Object> variables = new HashMap<>();
            variables.put("texto", texto);

            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("variables", variables);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            log.info("Request a filial: " + request.getBody());
            ResponseEntity<String> responseString = restTemplate.postForEntity(url, request, String.class);
            log.info("Response string from filial: " + responseString.getBody());
            
            Map<String, Object> responseBody = objectMapper.readValue(responseString.getBody(), Map.class);

            if (responseBody == null || responseBody.containsKey("errors")) {
                log.error("Error al buscar maletin en filial: " + responseString.getBody());
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data == null || data.get("maletinPorDescripcion") == null) {
                return null;
            }

            Map<String, Object> maletinMap = (Map<String, Object>) data.get("maletinPorDescripcion");
            Maletin maletin = new Maletin();
            maletin.setId(maletinMap.get("id") != null ? Long.valueOf(maletinMap.get("id").toString()) : null);
            maletin.setDescripcion((String) maletinMap.get("descripcion"));
            maletin.setActivo((Boolean) maletinMap.get("activo"));
            maletin.setAbierto((Boolean) maletinMap.get("abierto"));

            return maletin;
        } catch (Exception e) {
            log.error("Error en proxy maletinPorDescripcionPorSucursal", e);
            return null;
        }
    }

    public Boolean deleteMaletin(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countMaletin() {
        return service.count();
    }

}
