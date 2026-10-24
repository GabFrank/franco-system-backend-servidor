package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionSistema;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.empresarial.ConfiguracionSistemaRepository;
import com.franco.dev.service.operaciones.compras.ComprasSecurityService;
import com.franco.dev.service.personas.RoleService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ConfiguracionSistemaService {

    public static final String CLAVE_OPENAI_API_KEY = "openai.api_key";
    public static final String CLAVE_OPENAI_MODELO = "openai.modelo";
    public static final String CLAVE_OPENAI_PROMPT = "openai.prompt_adicional";
    public static final String MODELO_POR_DEFECTO = "gpt-4o";

    private final ConfiguracionSistemaRepository repository;
    private final RoleService roleService;
    private final ComprasSecurityService seguridad;
    private final CifradoConfiguracion cifrado;
    private final String baseUrl;

    @Autowired
    public ConfiguracionSistemaService(ConfiguracionSistemaRepository repository,
                                       RoleService roleService,
                                       ComprasSecurityService seguridad,
                                       @Value("${CONFIG_MASTER_KEY:}") String masterKey,
                                       @Value("${app.openai.base-url:https://api.openai.com}") String baseUrl) {
        this.repository = repository;
        this.roleService = roleService;
        this.seguridad = seguridad;
        this.cifrado = new CifradoConfiguracion(masterKey);
        this.baseUrl = baseUrl;
    }

    /** Valor de la clave; descifra si está marcada como encriptada. Vacío si no hay valor. */
    public Optional<String> valor(String clave) {
        Optional<ConfiguracionSistema> c = repository.findById(clave);
        if (!c.isPresent() || c.get().getValor() == null || c.get().getValor().isEmpty()) return Optional.empty();
        if (Boolean.TRUE.equals(c.get().getEncriptado())) {
            if (!cifrado.disponible()) {
                throw new GraphQLException("Falta CONFIG_MASTER_KEY en el servidor: no se puede leer la clave cifrada.");
            }
            return Optional.of(cifrado.descifrar(c.get().getValor()));
        }
        return Optional.of(c.get().getValor());
    }

    /** Guarda el valor (cifrado si la fila está marcada como encriptada). null o "" borra el valor. */
    public void guardar(String clave, String valor) {
        ConfiguracionSistema c = repository.findById(clave).orElseGet(() -> {
            ConfiguracionSistema n = new ConfiguracionSistema();
            n.setClave(clave);
            n.setEncriptado(false);
            return n;
        });
        if (Boolean.TRUE.equals(c.getEncriptado()) && valor != null && !valor.isEmpty()) {
            if (!cifrado.disponible()) {
                throw new GraphQLException("Falta CONFIG_MASTER_KEY en el servidor: no se puede guardar la clave cifrada.");
            }
            c.setValor(cifrado.cifrar(valor));
        } else {
            c.setValor(valor == null || valor.isEmpty() ? "" : valor);
        }
        c.setModificadoEn(LocalDateTime.now());
        repository.save(c);
    }

    public ConfiguracionIa configuracionIa() {
        String key = null;
        try {
            key = valor(CLAVE_OPENAI_API_KEY).orElse(null);
        } catch (RuntimeException e) {
            // sin master key o clave ilegible: se informa como no cargada
        }
        String fin = key != null ? "…" + key.substring(Math.max(0, key.length() - 4)) : null;
        String modelo = valor(CLAVE_OPENAI_MODELO).orElse(MODELO_POR_DEFECTO);
        String prompt = valor(CLAVE_OPENAI_PROMPT).orElse("");
        return new ConfiguracionIa(key != null, fin, modelo, prompt, cifrado.disponible());
    }

    public ConfiguracionIa guardarConfiguracionIa(String apiKey, String modelo, String promptAdicional) {
        exigirAdmin();
        if (apiKey != null) guardar(CLAVE_OPENAI_API_KEY, apiKey.trim());
        if (modelo != null) guardar(CLAVE_OPENAI_MODELO, modelo.trim().isEmpty() ? MODELO_POR_DEFECTO : modelo.trim());
        if (promptAdicional != null) guardar(CLAVE_OPENAI_PROMPT, promptAdicional);
        return configuracionIa();
    }

    public String probarConexionIa() {
        exigirAdmin();
        String key = valor(CLAVE_OPENAI_API_KEY).orElseThrow(
                () -> new GraphQLException("Cargá primero la clave de OpenAI."));
        String modelo = valor(CLAVE_OPENAI_MODELO).orElse(MODELO_POR_DEFECTO);
        HttpURLConnection con = null;
        try {
            String url = baseUrl.replaceAll("/+$", "") + "/v1/models/" + URLEncoder.encode(modelo, "UTF-8");
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            con.setRequestProperty("Authorization", "Bearer " + key);
            int status = con.getResponseCode();
            if (status >= 200 && status < 300) return "Conexión OK con " + modelo;
            String cuerpo = leer(con.getErrorStream());
            return "Error de OpenAI (" + status + "): " + truncar(cuerpo.replace(key, "***"), 300);
        } catch (Exception e) {
            return "No se pudo conectar: " + truncar(String.valueOf(e.getMessage()).replace(key, "***"), 300);
        } finally {
            if (con != null) con.disconnect();
        }
    }

    public void exigirAdmin() {
        Usuario u = seguridad.usuarioActual();
        if (u != null) {
            for (Role r : roleService.findByUsuarioId(u.getId())) {
                if (r.getNombre() != null && ComprasSecurityService.ADMIN.equalsIgnoreCase(r.getNombre().trim())) return;
            }
        }
        throw new GraphQLException("No autorizado: hace falta el rol ADMIN.");
    }

    private static String leer(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null && sb.length() < 1000) sb.append(l);
        }
        return sb.toString();
    }

    private static String truncar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
