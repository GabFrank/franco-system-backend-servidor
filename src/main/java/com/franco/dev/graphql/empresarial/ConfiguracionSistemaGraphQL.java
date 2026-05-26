package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionSistema;
import com.franco.dev.service.empresarial.ConfiguracionSistemaService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;

@Component
public class ConfiguracionSistemaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionSistemaGraphQL.class);

    @Autowired
    private ConfiguracionSistemaService service;

    public Optional<ConfiguracionSistema> configuracionSistema(String clave) {
        return service.findByClaveMasked(clave);
    }

    public ConfiguracionSistema setConfiguracionSistema(String clave, String valor) {
        return service.set(clave, valor);
    }

    /**
     * Hace una llamada minima a https://api.openai.com/v1/models con la API key configurada.
     * Retorna "OK" si responde 200, o un mensaje de error legible.
     * No expone la API key en el output.
     */
    public String testOpenAiConnection() {
        Optional<String> apiKeyOpt = service.getDecrypted("openai.api_key");
        if (!apiKeyOpt.isPresent()) {
            return "ERROR: API key no configurada";
        }
        String apiKey = apiKeyOpt.get();

        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.openai.com/v1/models");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);

            int code = connection.getResponseCode();
            if (code == 200) {
                return "OK";
            }
            return "ERROR: HTTP " + code + " - " + connection.getResponseMessage();
        } catch (Exception e) {
            log.error("Fallo en testOpenAiConnection: {}", e.getMessage());
            return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
