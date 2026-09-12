package com.franco.dev.controller;

import com.franco.dev.service.financiero.CapturaMuestraService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * La foto del cupon de muestra, del lado del telefono.
 *
 * <p><b>Por que REST y no GraphQL.</b> Del otro lado hay un navegador pelado: carga una pagina y
 * sube un archivo. No hay cliente Apollo, no hay token de sesion. Es la misma excepcion deliberada
 * que ya hace {@code CapturaCuponController} del filial, por el mismo motivo.
 *
 * <p><b>Por que cuelga de {@code /public}.</b> El telefono no tiene login ni rol, asi que la ruta
 * queda fuera de la autenticacion. {@code /public/**} ya es el espacio {@code permitAll} de
 * {@code SecurityConfig}.
 *
 * <p><b>Quien autoriza entonces.</b> El token, y nada mas. Solo lo puede emitir alguien que ya paso
 * por el ABM de formatos con rol de tesoreria, vence en minutos, y lo unico que habilita es subir
 * una imagen que se descarta al rato. No toca ninguna venta ni ninguna caja: <b>esto configura, no
 * registra</b>.
 *
 * <p><b>La diferencia con el filial: aca hay HTTPS.</b> El filial sirve su pagina por HTTP plano en
 * la LAN --origen privado hacia privado, sin certificado-- y por eso la foto entra por
 * {@code <input type="file" capture>}, que delega en la app de camara del telefono. Central, que ya
 * corre detras de nginx con certificado, no tiene esa restriccion. La pagina es la misma por ahora;
 * la cámara dentro de la página queda disponible el dia que se quiera.
 *
 * <p>⚠️ <b>El telefono tiene que poder alcanzar a central.</b> En la instancia productiva eso ya
 * pasa; en alpha, que vive en mauro sin IP publica, puede no pasar. Por eso el camino que no
 * depende de nada es el otro: subir la foto desde el ABM, que va por la sesion del navegador que ya
 * esta hablando con central.
 */
@Slf4j
@RestController
@RequestMapping("/public/captura-muestra")
public class CapturaMuestraController {

    private static final String PAGINA = "captura-muestra/captura-muestra.html";
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private final CapturaMuestraService service;

    public CapturaMuestraController(CapturaMuestraService service) {
        this.service = service;
    }

    /** La pagina que abre el telefono al escanear el QR. */
    @GetMapping(value = "/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> pagina(@PathVariable String token) {
        Optional<CapturaMuestraService.Muestra> m = service.porToken(token);
        if (!m.isPresent() || m.get().vencida()) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .contentType(MediaType.TEXT_HTML)
                    .body(aviso("Este código ya no sirve",
                            "Pedí uno nuevo desde la pantalla de formatos."));
        }
        try (InputStream in = new ClassPathResource(PAGINA).getInputStream()) {
            return ResponseEntity.ok(new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("no se pudo servir la pagina de captura de muestra", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(aviso("No se pudo abrir", "Avisá al soporte."));
        }
    }

    /**
     * La foto. El cuerpo es el JPEG crudo.
     *
     * <p>Sirve para las dos puertas: el telefono que escaneo el QR, y el ABM del desktop subiendo
     * un archivo del disco. Es la misma operacion y no vale la pena duplicarla.
     */
    @PostMapping(value = "/{token}", consumes = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<?> subir(@PathVariable String token,
                                   @RequestBody(required = false) byte[] jpeg) {
        // `required = false` para que el chequeo de abajo sea el que conteste. Con el default
        // Spring rechaza la request ANTES del handler y responde el JSON de error del framework
        // --con stack trace-- en un endpoint sin autenticacion. Mismo tropiezo ya visto en el
        // filial, verificado alla el 2026-09-10 mandando un POST vacio.
        if (jpeg == null || jpeg.length == 0) {
            return ResponseEntity.badRequest().body("la foto llego vacia");
        }
        if (jpeg.length > MAX_BYTES) {
            return ResponseEntity.badRequest().body("la foto es demasiado grande");
        }
        try {
            CapturaMuestraService.Muestra m = service.recibir(token, jpeg);

            // ERROR no es 500: es un desenlace previsto y REINTENTABLE. Acá más que en el filial,
            // porque el token de muestra no se consume: se saca otra foto y listo.
            if ("ERROR".equals(m.estado)) {
                return ResponseEntity.unprocessableEntity().body(m.error);
            }

            Map<String, Object> r = new HashMap<String, Object>();
            r.put("estado", m.estado);
            r.put("lineas", m.textoOcr == null ? 0 : m.textoOcr.split("\n").length);
            r.put("ms", m.msOcr);
            return ResponseEntity.ok(r);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(e.getMessage());
        } catch (Exception e) {
            log.error("fallo la subida de la muestra", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("no se pudo procesar la foto");
        }
    }

    private static String aviso(String titulo, String detalle) {
        return "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + titulo + "</title></head>"
                + "<body style=\"font-family:system-ui;padding:32px;text-align:center\">"
                + "<h1 style=\"font-size:20px\">" + titulo + "</h1>"
                + "<p style=\"opacity:.7\">" + detalle + "</p></body></html>";
    }
}
