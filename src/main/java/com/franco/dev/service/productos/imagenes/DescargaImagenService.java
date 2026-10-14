package com.franco.dev.service.productos.imagenes;

import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baja una foto sugerida y la devuelve como data URL, para que el desktop la pase por el
 * recorte y la guarde con {@code saveImagenPresentacion} como cualquier foto.
 *
 * <p>Lo hace el central y no el desktop porque muchos sitios no dejan leer sus imágenes desde
 * otro origen (CORS). A cambio el central no puede ser un proxy abierto: solo baja URLs que
 * él mismo devolvió en una búsqueda reciente, y nunca de una dirección de la red interna
 * (ni siguiendo una redirección).
 */
@Service
public class DescargaImagenService {

    private static final Logger log = LoggerFactory.getLogger(DescargaImagenService.class);

    private static final long VIGENCIA_MS = 2 * 60 * 60 * 1000L;
    private static final int MAX_REGISTRADAS = 20000;
    private static final int MAX_REDIRECCIONES = 3;

    /** URL emitida por una búsqueda → cuándo. */
    private final Map<String, Long> emitidas = new ConcurrentHashMap<>();
    private final long maxBytes;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public DescargaImagenService(@Value("${imagenes.sugerencias.max-bytes:10485760}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public void registrar(Collection<String> urls) {
        long ahora = System.currentTimeMillis();
        if (emitidas.size() > MAX_REGISTRADAS) {
            emitidas.values().removeIf(t -> ahora - t > VIGENCIA_MS);
            if (emitidas.size() > MAX_REGISTRADAS) emitidas.clear();
        }
        for (String u : urls) {
            if (u != null) emitidas.put(u, ahora);
        }
    }

    public String descargar(String url) {
        Long emitida = url == null ? null : emitidas.get(url);
        if (emitida == null || System.currentTimeMillis() - emitida > VIGENCIA_MS) {
            throw new GraphQLException("Esa imagen no salió de una búsqueda reciente: volvé a buscar.");
        }
        URI actual;
        try {
            actual = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new GraphQLException("La dirección de la imagen no es válida.");
        }
        try {
            for (int salto = 0; salto <= MAX_REDIRECCIONES; salto++) {
                validarDestino(actual);
                HttpRequest req = HttpRequest.newBuilder(actual)
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", ClienteJsonImagenes.USER_AGENT)
                        .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8")
                        .GET().build();
                HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                int st = res.statusCode();
                if (st == 301 || st == 302 || st == 303 || st == 307 || st == 308) {
                    res.body().close();
                    String destino = res.headers().firstValue("Location")
                            .orElseThrow(() -> new GraphQLException("El sitio redirigió la imagen sin decir a dónde."));
                    actual = actual.resolve(destino);
                    continue;
                }
                if (st / 100 != 2) {
                    res.body().close();
                    throw new GraphQLException("El sitio no entregó la imagen (HTTP " + st + "). Probá con otra.");
                }
                long declarado = res.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (declarado > maxBytes) {
                    res.body().close();
                    throw new GraphQLException("La imagen es demasiado pesada.");
                }
                byte[] bytes = leerConTope(res.body(), maxBytes);
                String mime = tipoImagen(bytes);
                if (mime == null) {
                    throw new GraphQLException("El archivo no es una imagen JPG, PNG, GIF o WEBP. Probá con otra.");
                }
                return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            }
            throw new GraphQLException("La imagen redirige demasiadas veces. Probá con otra.");
        } catch (IOException e) {
            log.info("No se pudo bajar la imagen sugerida {}: {}", url, e.toString());
            throw new GraphQLException("No se pudo bajar la imagen. Probá con otra.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GraphQLException("Se interrumpió la descarga de la imagen.");
        }
    }

    static void validarDestino(URI u) throws UnknownHostException {
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new GraphQLException("La dirección de la imagen no es http(s).");
        }
        if (u.getHost() == null) {
            throw new GraphQLException("La dirección de la imagen no es válida.");
        }
        for (InetAddress a : InetAddress.getAllByName(u.getHost())) {
            if (esInterna(a)) {
                throw new GraphQLException("La imagen apunta a una dirección interna: no se descarga.");
            }
        }
    }

    static boolean esInterna(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            return b0 == 0 || (b0 == 100 && b1 >= 64 && b1 <= 127); // 0/8 y CGNAT 100.64/10
        }
        if (a instanceof Inet6Address) {
            return (b[0] & 0xfe) == 0xfc; // fc00::/7, direcciones locales únicas
        }
        return false;
    }

    static byte[] leerConTope(InputStream in, long tope) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            long total = 0;
            int n;
            while ((n = is.read(buf)) != -1) {
                total += n;
                if (total > tope) throw new GraphQLException("La imagen es demasiado pesada.");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    /** Tipo por la firma del archivo, no por lo que diga el sitio. */
    static String tipoImagen(byte[] b) {
        if (b.length < 12) return null;
        if ((b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) return "image/jpeg";
        if ((b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "image/gif";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
