package com.franco.dev.service.productos.imagenes;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Brave Search API (imágenes). Es paga por búsqueda con un crédito mensual gratis, así que solo
 * se usa de respaldo cuando Bing falla o no trae nada. Sin
 * {@code imagenes.sugerencias.brave.api-key} queda apagada.
 */
@Component
public class BraveImagenes {

    private static final String API = "https://api.search.brave.com/res/v1/images/search";

    private final ClienteHttpImagenes cliente;
    private final String apiKey;

    public BraveImagenes(ClienteHttpImagenes cliente,
                         @Value("${imagenes.sugerencias.brave.api-key:}") String apiKey) {
        this.cliente = cliente;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean habilitada() {
        return !apiKey.isEmpty();
    }

    public List<ImagenSugerida> buscar(String q) throws IOException {
        String url = API + "?count=50&safesearch=strict&q=" + ClienteHttpImagenes.enc(q);
        return parsear(cliente.get(url, Map.of("X-Subscription-Token", apiKey)));
    }

    static List<ImagenSugerida> parsear(JsonNode raiz) {
        List<ImagenSugerida> lista = new ArrayList<>();
        if (raiz == null) return lista;
        for (JsonNode r : raiz.path("results")) {
            JsonNode props = r.path("properties");
            String img = ClienteHttpImagenes.texto(props, "url");
            if (!ClienteHttpImagenes.esHttp(img)) continue;
            String mini = ClienteHttpImagenes.texto(r.path("thumbnail"), "src");
            if (!ClienteHttpImagenes.esHttp(mini)) mini = img;
            String sitio = ClienteHttpImagenes.texto(r, "source");
            if (sitio == null) sitio = ClienteHttpImagenes.dominio(ClienteHttpImagenes.texto(r, "url"));
            lista.add(new ImagenSugerida(img, mini,
                    ClienteHttpImagenes.entero(props, "width"), ClienteHttpImagenes.entero(props, "height"),
                    ClienteHttpImagenes.texto(r, "title"), sitio, ClienteHttpImagenes.texto(r, "url"), ImagenSugerida.BRAVE));
        }
        return lista;
    }
}
