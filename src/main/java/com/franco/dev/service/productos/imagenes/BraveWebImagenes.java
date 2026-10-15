package com.franco.dev.service.productos.imagenes;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brave Search leído directo, sin API key: la página de resultados trae los datos como un objeto
 * JavaScript ({@code {title:"…",url:"…",…,thumbnail:{…},properties:{url:"…"}}}) que acá se lee con
 * expresiones regulares. Es la fuente para buscar <b>por código de barras</b>: a diferencia de Bing,
 * con el número solo trae la foto del producto exacto (supermercados que nombran la foto con el código,
 * páginas de producto que lo citan). Probado el 2026-10-15 con 5 códigos.
 *
 * <ul>
 *   <li>{@link #imagenes}: {@code /images}. Cada resultado trae además una URL firmada de «imágenes
 *       parecidas» ({@code similar_images_api_url}), que es búsqueda visual: {@link #parecidas}.</li>
 *   <li>{@link #paginas}: {@code /search}. De cada página, la foto que Brave muestra al lado
 *       (su {@code og:image}).</li>
 * </ul>
 * No es una API con contrato: si Brave cambia la página, deja de traer resultados (no rompe nada,
 * queda el aviso en el panel) y hay que ajustar este parser.
 *
 * <p>Brave corta con HTTP 429 si se le pregunta seguido (pasó con ~20 consultas en pocos segundos), y
 * cada servicio por su lado: con {@code /search} en 429, {@code /images} seguía respondiendo. Cada
 * respuesta se guarda {@value #CACHE_MIN} minutos y, después de un 429, a ese servicio no se le pregunta
 * por {@value #PAUSA_429_S} s.
 */
@Component
public class BraveWebImagenes {

    static final String BASE = "https://search.brave.com";
    private static final String PREFIJO_PARECIDAS = "/api/images/similar?";
    private static final Map<String, String> HEADERS = Map.of("Accept-Language", "es-PY,es;q=0.9");

    private static final String CADENA = "\"((?:[^\"\\\\]|\\\\.)*)\"";
    private static final Pattern INICIO = Pattern.compile("\\{title:" + CADENA + ",url:" + CADENA);
    private static final Pattern SOURCE = Pattern.compile("[,{]source:" + CADENA);
    private static final Pattern MINIATURA = Pattern.compile("thumbnail:\\{src:" + CADENA);
    private static final Pattern ORIGINAL = Pattern.compile("thumbnail:\\{[^{}]*?original:" + CADENA);
    private static final Pattern LOGO = Pattern.compile("thumbnail:\\{[^{}]*?logo:(true|false)");
    private static final Pattern PROPIEDADES = Pattern.compile("properties:\\{url:" + CADENA + "[^{}]*?height:(\\d+),width:(\\d+)");
    private static final Pattern PARECIDAS = Pattern.compile("similar_images_api_url:" + CADENA);
    private static final Pattern UNICODE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    /**
     * No es la foto de un producto: generadores de códigos de barras o QR, «sin imagen» de las tiendas,
     * logos, avatares y marcas (visto en los resultados reales del 2026-10-15).
     */
    private static final Pattern NO_ES_PRODUCTO = Pattern.compile("(?i)\\.svg(\\?|$)|barcode|qrcode|kodebar|placeholder"
            + "|no-?disponible|sin-imagen|no[-_]?image|no-photo|avatar|logo|/brands?/|/sellers?/");

    static final int CACHE_MIN = 30;
    static final int PAUSA_429_S = 120;
    private static final int CACHE_MAX = 500;

    private final ClienteHttpImagenes cliente;
    private final boolean habilitada;
    /** URL pedida → {cuándo, resultados}. */
    private final Map<String, Object[]> cache = new ConcurrentHashMap<>();
    /** Servicio de Brave → hasta cuándo no preguntarle (después de un 429). */
    private final Map<String, Long> pausaHasta = new ConcurrentHashMap<>();

    public BraveWebImagenes(ClienteHttpImagenes cliente,
                            @Value("${imagenes.sugerencias.brave-web.enabled:true}") boolean habilitada) {
        this.cliente = cliente;
        this.habilitada = habilitada;
    }

    public boolean habilitada() {
        return habilitada;
    }

    public List<ImagenSugerida> imagenes(String q) throws IOException {
        return cacheado("imagenes", BASE + "/images?safesearch=strict&q=" + ClienteHttpImagenes.enc(q), BraveWebImagenes::parsearImagenes);
    }

    public List<ImagenSugerida> paginas(String q) throws IOException {
        return cacheado("paginas", BASE + "/search?safesearch=strict&q=" + ClienteHttpImagenes.enc(q), BraveWebImagenes::parsearPaginas);
    }

    /**
     * Fotos visualmente parecidas a una de {@link #imagenes}.
     *
     * @param similares la ruta firmada que devolvió Brave; solo se acepta esa ruta de Brave, así el
     *                  central no le pide a Brave (ni a nadie) otra cosa en nombre del desktop.
     */
    public List<ImagenSugerida> parecidas(String similares) throws IOException {
        if (!esRutaParecidas(similares)) throw new IllegalArgumentException("Ruta de parecidas inválida");
        esperarPausa("parecidas");
        try {
            return parsearParecidas(cliente.get(BASE + similares, HEADERS));
        } catch (IOException e) {
            registrarPausa("parecidas", e);
            throw e;
        }
    }

    /** Se guardan los resultados y no el HTML (~200 KB por página). Se devuelven copias: quien las recibe las marca (exacta). */
    @SuppressWarnings("unchecked")
    private List<ImagenSugerida> cacheado(String servicio, String url, Function<String, List<ImagenSugerida>> parser)
            throws IOException {
        long ahora = System.currentTimeMillis();
        Object[] guardada = cache.get(url);
        if (guardada != null && ahora - (Long) guardada[0] < CACHE_MIN * 60_000L) {
            return copias((List<ImagenSugerida>) guardada[1]);
        }
        esperarPausa(servicio);
        List<ImagenSugerida> l;
        try {
            l = parser.apply(cliente.getTexto(url, HEADERS));
        } catch (IOException e) {
            registrarPausa(servicio, e);
            throw e;
        }
        if (cache.size() >= CACHE_MAX) {
            cache.values().removeIf(v -> ahora - (Long) v[0] >= CACHE_MIN * 60_000L);
            if (cache.size() >= CACHE_MAX) cache.clear();
        }
        // Vacío puede ser que Brave cambió la página o mostró un captcha: no se guarda, se reintenta la próxima.
        if (!l.isEmpty()) cache.put(url, new Object[]{ahora, l});
        return copias(l);
    }

    private static List<ImagenSugerida> copias(List<ImagenSugerida> l) {
        List<ImagenSugerida> res = new ArrayList<>(l.size());
        for (ImagenSugerida i : l) {
            ImagenSugerida c = new ImagenSugerida(i.getUrl(), i.getMiniatura(), i.getAncho(), i.getAlto(), i.getTitulo(),
                    i.getSitio(), i.getPagina(), i.getFuente());
            c.setSimilares(i.getSimilares());
            res.add(c);
        }
        return res;
    }

    private void esperarPausa(String servicio) throws IOException {
        if (System.currentTimeMillis() < pausaHasta.getOrDefault(servicio, 0L)) {
            throw new IOException("Brave pidió esperar (HTTP 429)");
        }
    }

    private void registrarPausa(String servicio, IOException e) {
        if (e.getMessage() != null && e.getMessage().contains("HTTP 429")) {
            pausaHasta.put(servicio, System.currentTimeMillis() + PAUSA_429_S * 1000L);
        }
    }

    static boolean esRutaParecidas(String ruta) {
        return ruta != null && ruta.startsWith(PREFIJO_PARECIDAS) && !ruta.contains("..") && !ruta.contains("@")
                && !ruta.contains("#") && ruta.indexOf("//") < 0;
    }

    static List<ImagenSugerida> parsearImagenes(String html) {
        List<ImagenSugerida> lista = new ArrayList<>();
        for (String[] r : resultados(html)) {
            String bloque = r[2];
            Matcher p = PROPIEDADES.matcher(bloque);
            if (!p.find()) continue;
            String img = desescapar(p.group(1));
            if (!ClienteHttpImagenes.esHttp(img) || NO_ES_PRODUCTO.matcher(img).find()) continue;
            String mini = primero(MINIATURA, bloque);
            ImagenSugerida i = new ImagenSugerida(img, ClienteHttpImagenes.esHttp(mini) ? mini : img,
                    entero(p.group(3)), entero(p.group(2)), r[0], sitio(bloque, r[1], img), r[1], ImagenSugerida.BRAVE_WEB);
            String similares = primero(PARECIDAS, bloque);
            if (esRutaParecidas(similares)) i.setSimilares(similares);
            lista.add(i);
        }
        return lista;
    }

    static List<ImagenSugerida> parsearPaginas(String html) {
        List<ImagenSugerida> lista = new ArrayList<>();
        for (String[] r : resultados(html)) {
            String bloque = r[2];
            String img = primero(ORIGINAL, bloque);
            if (!ClienteHttpImagenes.esHttp(img) || NO_ES_PRODUCTO.matcher(img).find()) continue;
            Matcher logo = LOGO.matcher(bloque);
            if (logo.find() && logo.group(1).equals("true")) continue;
            String mini = primero(MINIATURA, bloque);
            lista.add(new ImagenSugerida(img, ClienteHttpImagenes.esHttp(mini) ? mini : img, null, null,
                    r[0], sitio(bloque, r[1], img), r[1], ImagenSugerida.BRAVE_WEB));
        }
        return lista;
    }

    /** La respuesta de parecidas es JSON con la misma forma que los resultados de {@code /images}. */
    static List<ImagenSugerida> parsearParecidas(JsonNode raiz) {
        List<ImagenSugerida> lista = new ArrayList<>();
        if (raiz == null) return lista;
        for (JsonNode r : raiz.path("results")) {
            JsonNode props = r.path("properties");
            String img = ClienteHttpImagenes.texto(props, "url");
            if (!ClienteHttpImagenes.esHttp(img) || NO_ES_PRODUCTO.matcher(img).find()) continue;
            String mini = ClienteHttpImagenes.texto(r.path("thumbnail"), "src");
            String pagina = ClienteHttpImagenes.texto(r, "url");
            String sitio = ClienteHttpImagenes.texto(r, "source");
            ImagenSugerida i = new ImagenSugerida(img, ClienteHttpImagenes.esHttp(mini) ? mini : img,
                    ClienteHttpImagenes.entero(props, "width"), ClienteHttpImagenes.entero(props, "height"),
                    ClienteHttpImagenes.texto(r, "title"), sitio != null ? sitio : ClienteHttpImagenes.dominio(pagina != null ? pagina : img),
                    pagina, ImagenSugerida.BRAVE_WEB);
            String similares = ClienteHttpImagenes.texto(r, "similar_images_api_url");
            if (esRutaParecidas(similares)) i.setSimilares(similares);
            lista.add(i);
        }
        return lista;
    }

    /** Cada resultado: {título, url de la página, texto hasta el próximo resultado}. */
    private static List<String[]> resultados(String html) {
        List<String[]> res = new ArrayList<>();
        if (html == null) return res;
        Matcher m = INICIO.matcher(html);
        List<int[]> inicios = new ArrayList<>();
        List<String[]> datos = new ArrayList<>();
        while (m.find()) {
            inicios.add(new int[]{m.start(), m.end()});
            datos.add(new String[]{desescapar(m.group(1)), desescapar(m.group(2))});
        }
        for (int k = 0; k < inicios.size(); k++) {
            int fin = k + 1 < inicios.size() ? inicios.get(k + 1)[0] : html.length();
            String titulo = datos.get(k)[0];
            res.add(new String[]{titulo.isEmpty() ? null : titulo, datos.get(k)[1],
                    html.substring(inicios.get(k)[1], fin)});
        }
        return res;
    }

    private static String sitio(String bloque, String pagina, String img) {
        String s = primero(SOURCE, bloque);
        if (s != null && !s.isEmpty() && !s.contains(" ")) return s.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        String d = ClienteHttpImagenes.dominio(pagina);
        return d != null ? d : ClienteHttpImagenes.dominio(img);
    }

    private static String primero(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? desescapar(m.group(1)) : null;
    }

    private static Integer entero(String s) {
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Escapes de una cadena JavaScript: {@code <}, {@code \"}, {@code \/}, {@code \\}. */
    static String desescapar(String s) {
        if (s == null) return null;
        Matcher m = UNICODE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        }
        m.appendTail(sb);
        return sb.toString().replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\").trim();
    }
}
