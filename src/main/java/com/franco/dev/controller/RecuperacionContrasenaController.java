package com.franco.dev.controller;

import com.franco.dev.service.personas.CaptchaService;
import com.franco.dev.service.personas.RecuperacionContrasenaService;
import com.franco.dev.service.personas.RecuperacionContrasenaService.RecuperacionException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Map;

/**
 * "¿Olvido su contrasena?" del login. La logica esta en {@link RecuperacionContrasenaService}.
 *
 * <p><b>Por que REST y bajo {@code /public}.</b> Quien lo usa todavia no inicio sesion: no hay
 * token, y {@code /graphql} lo exige. Es la misma excepcion deliberada que
 * {@code CapturaMuestraController}. Lo que protege estas rutas es el captcha, el bloqueo por
 * intentos y los codigos de un solo uso, no la sesion.
 */
@Slf4j
@RestController
@RequestMapping("/public/recuperacion-contrasena")
public class RecuperacionContrasenaController {

    private final RecuperacionContrasenaService service;
    private final CaptchaService captchaService;

    public RecuperacionContrasenaController(RecuperacionContrasenaService service, CaptchaService captchaService) {
        this.service = service;
        this.captchaService = captchaService;
    }

    @Data
    public static class VerificarRequest {
        private String nickname;
        private String documento;
        /** yyyy-MM-dd */
        private String nacimiento;
        private String captchaId;
        private String captchaRespuesta;
    }

    @Data
    public static class SolicitudRrhhRequest {
        private String verificacionToken;
        private String telefono;
        private String email;
        private String observacion;
    }

    @Data
    public static class CodigoRequest {
        private Long solicitudId;
        private String codigo;
        /** Solo para las solicitudes a RRHH. */
        private String documento;
        private String nuevaContrasena;
    }

    @GetMapping("/captcha")
    public ResponseEntity<?> captcha() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(captchaService.generar());
    }

    @PostMapping("/verificar")
    public ResponseEntity<?> verificar(@RequestBody VerificarRequest req, HttpServletRequest http) {
        LocalDate nacimiento;
        try {
            nacimiento = req.getNacimiento() == null ? null : LocalDate.parse(req.getNacimiento().trim());
        } catch (DateTimeParseException e) {
            throw new RecuperacionException("La fecha de nacimiento no es válida.");
        }
        return ResponseEntity.ok(service.verificarIdentidad(req.getNickname(), req.getDocumento(), nacimiento,
                req.getCaptchaId(), req.getCaptchaRespuesta(), ip(http)));
    }

    @PostMapping("/solicitud-rrhh")
    public ResponseEntity<?> solicitudRrhh(@RequestBody SolicitudRrhhRequest req, HttpServletRequest http) {
        return ResponseEntity.ok(service.crearSolicitudRrhh(req.getVerificacionToken(), req.getTelefono(),
                req.getEmail(), req.getObservacion(), ip(http)));
    }

    @PostMapping("/consultar")
    public ResponseEntity<?> consultar(@RequestBody CodigoRequest req) {
        return ResponseEntity.ok(service.consultar(req.getSolicitudId(), req.getCodigo(), req.getDocumento()));
    }

    @PostMapping("/restablecer")
    public ResponseEntity<?> restablecer(@RequestBody CodigoRequest req) {
        return ResponseEntity.ok(service.restablecer(req.getSolicitudId(), req.getCodigo(), req.getDocumento(),
                req.getNuevaContrasena()));
    }

    @ExceptionHandler(RecuperacionException.class)
    public ResponseEntity<Map<String, String>> previsto(RecuperacionException e) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("message", e.getMessage()));
    }

    /** Lo que no estaba previsto no se le muestra al usuario tal cual: puede llevar detalles internos. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> imprevisto(Exception e) {
        log.error("Recuperacion de contrasena: error no previsto", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("message",
                "No se pudo procesar el pedido. Intente nuevamente en unos minutos."));
    }

    /**
     * Detras de nginx (central productivo) la IP real llega en X-Forwarded-For; se toma la ultima,
     * que es la que agrega nuestro proxy: las anteriores las puede escribir cualquiera. Si la
     * conexion no viene del propio servidor, el header se ignora.
     */
    private static String ip(HttpServletRequest http) {
        String remota = http.getRemoteAddr();
        String xff = http.getHeader("X-Forwarded-For");
        boolean desdeProxyLocal = "127.0.0.1".equals(remota) || "0:0:0:0:0:0:0:1".equals(remota) || "::1".equals(remota);
        if (desdeProxyLocal && xff != null && !xff.trim().isEmpty()) {
            String[] partes = xff.split(",");
            return partes[partes.length - 1].trim();
        }
        return remota;
    }
}
