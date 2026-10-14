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
 * se usa de respaldo cuando SearXNG no está o no trae nada. Sin
 * {@code imagenes.sugerencias.brave.api-key} queda apagada.
 */
@Component
public class BraveImagenes {

    private static final String API = "https://api.search.brave.com/res/v1/images/search";

    private final ClienteJsonImagenes cliente;
    private final String apiKey;

    public BraveImagenes(ClienteJsonImagenes cliente,
                         @Value("${imagenes.sugerencias.brave.api-key:}") String apiKey) {
        this.cliente = cliente;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean habilitada() {
        return !apiKey.isEmpty();
    }

    public List<ImagenSugerida> buscar(String q) throws IOException {
        String url = API + "?count=50&safesearch=strict&q=" + ClienteJsonImagenes.enc(q);
        return parsear(cliente.get(url, Map.of("X-Subscription-Token", apiKey)));
    }

    static List<ImagenSugerida> parsear(JsonNode raiz) {
        List<ImagenSugerida> lista = new ArrayList<>();
        if (raiz == null) return lista;
        for (JsonNode r : raiz.path("results")) {
            JsonNode props = r.path("properties");
            String img = ClienteJsonImagenes.texto(props, "url");
            if (!ClienteJsonImagenes.esHttp(img)) continue;
            String mini = ClienteJsonImagenes.texto(r.path("thumbnail"), "src");
            if (!ClienteJsonImagenes.esHttp(mini)) mini = img;
            String sitio = ClienteJsonImagenes.texto(r, "source");
            if (sitio == null) sitio = ClienteJsonImagenes.dominio(ClienteJsonImagenes.texto(r, "url"));
            lista.add(new ImagenSugerida(img, mini,
                    ClienteJsonImagenes.entero(props, "width"), ClienteJsonImagenes.entero(props, "height"),
                    ClienteJsonImagenes.texto(r, "title"), sitio, ImagenSugerida.BRAVE));
        }
        return lista;
    }
}
