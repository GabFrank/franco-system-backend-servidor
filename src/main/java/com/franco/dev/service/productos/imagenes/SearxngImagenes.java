package com.franco.dev.service.productos.imagenes;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SearXNG propio: metabuscador que junta las imágenes de Bing, DuckDuckGo, Google, Brave, etc.
 * Gratis y sin límite, pero hay que instalarlo aparte y habilitarle {@code json} en
 * {@code search.formats}. Sin {@code imagenes.sugerencias.searxng.url} queda apagado.
 */
@Component
public class SearxngImagenes {

    private static final Pattern RESOLUCION = Pattern.compile("(\\d+)\\s*[x×X]\\s*(\\d+)");

    private final ClienteJsonImagenes cliente;
    private final String baseUrl;

    public SearxngImagenes(ClienteJsonImagenes cliente,
                           @Value("${imagenes.sugerencias.searxng.url:}") String baseUrl) {
        this.cliente = cliente;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceFirst("/+$", "");
    }

    public boolean habilitada() {
        return !baseUrl.isEmpty();
    }

    public List<ImagenSugerida> buscar(String q) throws IOException {
        String url = baseUrl + "/search?format=json&categories=images&safesearch=1&q=" + ClienteJsonImagenes.enc(q);
        return parsear(cliente.get(url, Collections.emptyMap()));
    }

    static List<ImagenSugerida> parsear(JsonNode raiz) {
        List<ImagenSugerida> lista = new ArrayList<>();
        if (raiz == null) return lista;
        for (JsonNode r : raiz.path("results")) {
            String img = ClienteJsonImagenes.texto(r, "img_src");
            if (!ClienteJsonImagenes.esHttp(img)) continue;
            String mini = ClienteJsonImagenes.texto(r, "thumbnail_src");
            if (!ClienteJsonImagenes.esHttp(mini)) mini = img;
            Integer ancho = null, alto = null;
            String res = ClienteJsonImagenes.texto(r, "resolution");
            if (res != null) {
                Matcher m = RESOLUCION.matcher(res);
                if (m.find()) {
                    ancho = Integer.valueOf(m.group(1));
                    alto = Integer.valueOf(m.group(2));
                }
            }
            String pagina = ClienteJsonImagenes.texto(r, "url");
            String sitio = ClienteJsonImagenes.dominio(pagina != null ? pagina : img);
            lista.add(new ImagenSugerida(img, mini, ancho, alto, ClienteJsonImagenes.texto(r, "title"), sitio, ImagenSugerida.SEARXNG));
        }
        return lista;
    }
}
