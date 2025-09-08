package com.franco.dev.controller;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.EventoDte;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.financiero.DteService;
import com.franco.dev.service.financiero.EventoDteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/eventos-dte")
@RequiredArgsConstructor
@Slf4j
public class EventoDteController {

    private final EventoDteService eventoDteService;
    private final DteService dteService;
    private final UsuarioService usuarioService;
    /**
     * Cancela un documento electrónico
     */
    @PostMapping("/cancelar/{documentoId}")
    public ResponseEntity<Map<String, Object>> cancelarDocumento(
            @PathVariable Long documentoId,
            @RequestParam String motivo,
            @RequestParam(required = false) String observacion,
            @RequestParam Long usuarioId) {

        try {
            log.info("[EVENTO-DTE] Solicitud de cancelación del documento {}", documentoId);

            // Buscar el documento
            DocumentoElectronico documento = dteService.findById(documentoId);
            if (documento == null) {
                return ResponseEntity.notFound().build();
            }

            // Verificar que el documento pueda ser cancelado
            if (!puedeSerCancelado(documento)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "El documento no puede ser cancelado en su estado actual");
                error.put("estadoActual", documento.getEstadoSifen());
                return ResponseEntity.badRequest().body(error);
            }

            // Obtener el usuario de la base de datos
            Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
            if (usuario == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Usuario no encontrado");
                return ResponseEntity.badRequest().body(error);
            }

            // Registrar el evento de cancelación
            EventoDte evento = eventoDteService.registrarCancelacion(documento, motivo, usuario, observacion);

            // Actualizar el estado del documento (opcional - depende de la lógica de negocio)
            // documento.setEstadoSifen("CANCELADO");
            // dteService.save(documento);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mensaje", "Documento cancelado exitosamente");
            response.put("eventoId", evento.getId());
            response.put("documentoId", documentoId);

            log.info("[EVENTO-DTE] Documento {} cancelado exitosamente", documentoId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[EVENTO-DTE] Error cancelando documento {}", documentoId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error interno del servidor: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Obtiene todos los eventos de un documento
     */
    @GetMapping("/documento/{documentoId}")
    public ResponseEntity<List<EventoDte>> getEventosByDocumento(@PathVariable Long documentoId) {
        try {
            List<EventoDte> eventos = eventoDteService.findByDocumentoElectronicoId(documentoId);
            return ResponseEntity.ok(eventos);
        } catch (Exception e) {
            log.error("[EVENTO-DTE] Error obteniendo eventos del documento {}", documentoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obtiene todos los eventos
     */
    @GetMapping
    public ResponseEntity<List<EventoDte>> getAllEventos() {
        try {
            List<EventoDte> eventos = eventoDteService.findAll();
            return ResponseEntity.ok(eventos);
        } catch (Exception e) {
            log.error("[EVENTO-DTE] Error obteniendo todos los eventos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obtiene un evento por ID
     */
    @GetMapping("/{eventoId}")
    public ResponseEntity<EventoDte> getEventoById(@PathVariable Long eventoId) {
        try {
            EventoDte evento = eventoDteService.findById(eventoId);
            if (evento == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(evento);
        } catch (Exception e) {
            log.error("[EVENTO-DTE] Error obteniendo evento {}", eventoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Actualiza la respuesta de SIFEN para un evento
     */
    @PutMapping("/{eventoId}/respuesta-sifen")
    public ResponseEntity<Map<String, Object>> actualizarRespuestaSifen(
            @PathVariable Long eventoId,
            @RequestParam String cdcEvento,
            @RequestParam String mensajeRespuestaSifen) {

        try {
            eventoDteService.actualizarRespuestaSifen(eventoId, cdcEvento, mensajeRespuestaSifen);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mensaje", "Respuesta SIFEN actualizada exitosamente");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[EVENTO-DTE] Error actualizando respuesta SIFEN para evento {}", eventoId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error interno del servidor: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Endpoint de prueba
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "EventoDteController funcionando correctamente");
        response.put("timestamp", java.time.LocalDateTime.now().toString());

        Map<String, String> tiposEvento = new HashMap<>();
        tiposEvento.put("1", "Cancelación");
        tiposEvento.put("2", "Rechazo");
        tiposEvento.put("3", "Aprobación");
        tiposEvento.put("4", "Corrección");
        response.put("tiposEvento", tiposEvento);

        return ResponseEntity.ok(response);
    }

    /**
     * Verifica si un documento puede ser cancelado
     */
    private boolean puedeSerCancelado(DocumentoElectronico documento) {
        String estado = documento.getEstadoSifen();
        // Solo permitir cancelación en estados específicos
        return "GENERADO".equals(estado) ||
               "ENVIADO".equals(estado) ||
               "APROBADO".equals(estado) ||
               "PENDIENTE_APROBACION".equals(estado);
    }
}
