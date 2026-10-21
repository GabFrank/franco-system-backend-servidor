package com.franco.dev.service.productos.imagenes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bing Imágenes, leído directo desde el central: la misma página que carga el buscador web al
 * hacer scroll ({@code /images/async}), sin API key ni nada instalado aparte. Es como lo consulta
 * SearXNG. Cada resultado es un {@code <a class="iusc" m="{json}">}: el JSON trae la foto
 * ({@code murl}), la miniatura ({@code turl}), la página ({@code purl}) y el título ({@code t});
 * el {@code href} del mismo enlace trae el tamaño ({@code expw}/{@code exph}).
 *
 * <p>No es una API con contrato: si Bing cambia el HTML, deja de traer resultados (no rompe nada,
 * queda el aviso en el panel) y hay que ajustar este parser.
 */
@Component
public class BingImagenes {

    private static final String URL = "https://www.bing.com/images/async?mmasync=1&count=35&setlang=es&cc=PY&adlt=strict";
    /** Búsqueda segura estricta (lo que guarda la preferencia del sitio). */
    private static final Map<String, String> HEADERS = Map.of("Cookie", "SRCHHPGUSR=ADLT=STRICT", "Accept-Language", "es-PY,es;q=0.9");
    private static final Pattern ENLACE = Pattern.compile("<a\\s[^>]*?class=\"iusc\"[^>]*>");
    private static final Pattern META = Pattern.compile("\\sm=\"([^\"]*)\"");
    private static final Pattern ANCHO = Pattern.compile("expw=(\\d+)");
    private static final Pattern ALTO = Pattern.compile("exph=(\\d+)");
    private static final Pattern ENTIDAD_NUMERICA = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    private final ClienteHttpImagenes cliente;
    private final ObjectMapper objectMapper;
    private final boolean habilitada;

    public BingImagenes(ClienteHttpImagenes cliente, ObjectMapper objectMapper,
                        @Value("${imagenes.sugerencias.bing.enabled:true}") boolean habilitada) {
        this.cliente = cliente;
        this.objectMapper = objectMapper;
        this.habilitada = habilitada;
    }

    public boolean habilitada() {
        return habilitada;
    }

    /** @param pagina desde 0; 35 resultados por página. */
    public List<ImagenSugerida> buscar(String q, int pagina) throws IOException {
        String url = URL + "&first=" + (pagina * 35 + 1) + "&q=" + ClienteHttpImagenes.enc(q);
        return parsear(cliente.getTexto(url, HEADERS), objectMapper);
    }

    static List<ImagenSugerida> parsear(String html, ObjectMapper om) {
        List<ImagenSugerida> lista = new ArrayList<>();
        if (html == null) return lista;
        Matcher enlace = ENLACE.matcher(html);
        while (enlace.find()) {
            String tag = enlace.group();
            Matcher meta = META.matcher(tag);
            if (!meta.find()) continue;
            JsonNode m;
            try {
                m = om.readTree(desescapar(meta.group(1)));
            } catch (IOException e) {
                continue;
            }
            String img = ClienteHttpImagenes.texto(m, "murl");
            if (!ClienteHttpImagenes.esHttp(img)) continue;
            String mini = ClienteHttpImagenes.texto(m, "turl");
            if (!ClienteHttpImagenes.esHttp(mini)) mini = img;
            String pagina = ClienteHttpImagenes.texto(m, "purl");
            lista.add(new ImagenSugerida(img, mini, entero(ANCHO, tag), entero(ALTO, tag),
                    ClienteHttpImagenes.texto(m, "t"), ClienteHttpImagenes.dominio(pagina != null ? pagina : img),
                    pagina, ImagenSugerida.BING));
        }
        return lista;
    }

    private static Integer entero(Pattern p, String tag) {
        Matcher m = p.matcher(tag);
        if (!m.find()) return null;
        try {
            int v = Integer.parseInt(m.group(1));
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Entidades HTML de un atributo. {@code &amp;} va al final para no desescapar dos veces. */
    static String desescapar(String s) {
        Matcher m = ENTIDAD_NUMERICA.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int cp;
            try {
                cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException e) {
                cp = '?';
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
        }
        m.appendTail(sb);
        return sb.toString()
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
