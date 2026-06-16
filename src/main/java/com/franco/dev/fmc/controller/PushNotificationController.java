package com.franco.dev.fmc.controller;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.model.PushNotificationResponse;
import com.franco.dev.fmc.model.VentaStockCriticoNotificationRequest;
import com.franco.dev.fmc.service.NotificationRoleService;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.personas.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class PushNotificationController {

        private static final Logger log = LoggerFactory.getLogger(PushNotificationController.class);
        private static final DecimalFormat df = new DecimalFormat("#,###.##");

        private PushNotificationService pushNotificationService;

        @Autowired
        private NotificationTemplateService notificationTemplateService;

        @Autowired
        private UsuarioService usuarioService;

        @Autowired
        private SucursalService sucursalService;

        @Autowired
        private NotificationRoleService notificationRoleService;

        @Autowired
        private VentaCreditoService ventaCreditoService;

        public PushNotificationController(PushNotificationService pushNotificationService) {
                this.pushNotificationService = pushNotificationService;
        }

        @PostMapping("/notification/token")
        public ResponseEntity<PushNotificationResponse> sendTokenNotification(
                        @Valid @RequestBody PushNotificationRequest request) {
                pushNotificationService.sendPushNotificationToToken(request);
                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.ACCEPTED.value(),
                                "Notificación encolada para envío asíncrono."), HttpStatus.ACCEPTED);
        }

        @PostMapping("/notification/compra-credito/{ventaId}/{sucursalId}/{personaId}/{valorTotal}")
        public ResponseEntity<PushNotificationResponse> sendCompraCreditoNotification(
                        @PathVariable Long ventaId,
                        @PathVariable Long sucursalId,
                        @PathVariable Long personaId,
                        @PathVariable Double valorTotal,
                        @RequestParam(required = false) String sucursalNombre) {
                try {
                        if (sucursalNombre == null || sucursalNombre.isEmpty()) {
                                Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
                                sucursalNombre = sucursal != null ? sucursal.getNombre() : "";
                        }

                        Usuario usuarioCliente = usuarioService.findByPersonaId(personaId);
                        if (usuarioCliente == null) {
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.NOT_FOUND.value(),
                                                "No se encontró usuario para la persona indicada"),
                                                HttpStatus.NOT_FOUND);
                        }

                        Long ventaCreditoId = ventaId;
                        com.franco.dev.domain.financiero.VentaCredito ventaCredito = ventaCreditoService
                                        .findByVentaIdAndSucId(ventaId, sucursalId);
                        if (ventaCredito != null) {
                                ventaCreditoId = ventaCredito.getId();
                        }

                        PushNotificationRequest requestCliente = notificationTemplateService
                                        .compraCreditoRegistrada(ventaCreditoId, sucursalId, valorTotal, sucursalNombre,
                                                        df);
                        requestCliente.setUsuarioIds(Collections.singletonList(usuarioCliente.getId()));
                        pushNotificationService.sendPushNotificationToToken(requestCliente);

                        return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.ACCEPTED.value(),
                                        "Notificación enviada exitosamente"), HttpStatus.ACCEPTED);

                } catch (Exception e) {
                        return new ResponseEntity<>(
                                        new PushNotificationResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                        "Error al enviar notificación: " + e.getMessage()),
                                        HttpStatus.INTERNAL_SERVER_ERROR);
                }
        }

        @PostMapping("/notification/factura-alto-valor/{facturaId}/{sucursalId}/{valorTotal}/{clienteNombre}")
        public ResponseEntity<PushNotificationResponse> sendFacturaAltoValorNotification(
                        @PathVariable Long facturaId,
                        @PathVariable Long sucursalId,
                        @PathVariable Double valorTotal,
                        @PathVariable String clienteNombre) {
                try {
                        if (valorTotal < 3000000) {
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.BAD_REQUEST.value(),
                                                "Factura no cumple el mínimo de 3.000.000 Gs"), HttpStatus.BAD_REQUEST);
                        }

                        Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
                        List<String> rolesRelevantes = notificationRoleService.getRolesForFacturaAltoValor();
                        List<Long> usuariosRelevantes = notificationRoleService.getUserIdsByRoles(rolesRelevantes);

                        if (!usuariosRelevantes.isEmpty()) {
                                PushNotificationRequest request = notificationTemplateService.facturaAltoValor(
                                                facturaId, sucursalId, sucursal, valorTotal, clienteNombre, df);
                                request.setUsuarioIds(usuariosRelevantes);
                                pushNotificationService.sendPushNotificationToToken(request);
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.ACCEPTED.value(),
                                                "Notificación enviada exitosamente"), HttpStatus.ACCEPTED);
                        } else {
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.NOT_FOUND.value(),
                                                "No se encontraron usuarios con roles relevantes"),
                                                HttpStatus.NOT_FOUND);
                        }

                } catch (Exception e) {
                        return new ResponseEntity<>(
                                        new PushNotificationResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                        "Error al enviar notificación: " + e.getMessage()),
                                        HttpStatus.INTERNAL_SERVER_ERROR);
                }
        }

        @Autowired
        private com.franco.dev.service.financiero.GastoService gastoService;

        @Autowired
        private com.franco.dev.service.financiero.RetiroService retiroService;

        @PostMapping("/notification/gasto/{gastoId}/{sucursalId}/{personaId}/{valorTotal}")
        public ResponseEntity<PushNotificationResponse> sendGastoNotification(
                        @PathVariable Long gastoId,
                        @PathVariable Long sucursalId,
                        @PathVariable Long personaId,
                        @PathVariable Double valorTotal,
                        @RequestParam(required = false) String usuarioNombre,
                        @RequestParam(required = false) String sucursalNombre) {
                try {
                        if (sucursalNombre == null || sucursalNombre.isEmpty()) {
                                Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
                                sucursalNombre = sucursal != null ? sucursal.getNombre() : "";
                        }
                        if (usuarioNombre == null || usuarioNombre.isEmpty()) {
                                Usuario u = usuarioService.findByPersonaId(personaId);
                                usuarioNombre = u != null && u.getPersona() != null ? u.getPersona().getNombre() : "";
                        }

                        List<String> rolesRelevantes = notificationRoleService.getRolesForGastoRetiro();
                        List<Long> usuariosRelevantes = notificationRoleService.getUserIdsByRoles(rolesRelevantes);

                        if (!usuariosRelevantes.isEmpty()) {
                                PushNotificationRequest request = notificationTemplateService.gastoRealizado(
                                                gastoId, valorTotal, 0.0, 0.0, sucursalNombre, usuarioNombre, df);
                                request.setUsuarioIds(usuariosRelevantes);
                                pushNotificationService.sendPushNotificationToToken(request);
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.ACCEPTED.value(),
                                                "Notificación enviada exitosamente"), HttpStatus.ACCEPTED);
                        } else {
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.NOT_FOUND.value(),
                                                "No se encontraron usuarios con roles relevantes"),
                                                HttpStatus.NOT_FOUND);
                        }

                } catch (Exception e) {
                        return new ResponseEntity<>(
                                        new PushNotificationResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                        "Error al enviar notificación: " + e.getMessage()),
                                        HttpStatus.INTERNAL_SERVER_ERROR);
                }
        }

        @PostMapping("/notification/retiro/{retiroId}/{sucursalId}/{personaId}/{valorTotal}")
        public ResponseEntity<PushNotificationResponse> sendRetiroNotification(
                        @PathVariable Long retiroId,
                        @PathVariable Long sucursalId,
                        @PathVariable Long personaId,
                        @PathVariable Double valorTotal,
                        @RequestParam(required = false) String usuarioNombre,
                        @RequestParam(required = false) String sucursalNombre) {
                try {
                        // Si no se enviaron los nombres, intentar buscarlos en la DB como fallback
                        if (sucursalNombre == null || sucursalNombre.isEmpty()) {
                                Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
                                sucursalNombre = sucursal != null ? sucursal.getNombre() : "";
                        }
                        if (usuarioNombre == null || usuarioNombre.isEmpty()) {
                                Usuario u = usuarioService.findByPersonaId(personaId);
                                usuarioNombre = u != null && u.getPersona() != null ? u.getPersona().getNombre() : "";
                        }

                        List<String> rolesRelevantes = notificationRoleService.getRolesForGastoRetiro();
                        List<Long> usuariosRelevantes = notificationRoleService.getUserIdsByRoles(rolesRelevantes);

                        if (!usuariosRelevantes.isEmpty()) {
                                PushNotificationRequest request = notificationTemplateService.retiroRealizado(
                                                retiroId, sucursalNombre, usuarioNombre, valorTotal, df);
                                request.setUsuarioIds(usuariosRelevantes);
                                pushNotificationService.sendPushNotificationToToken(request);
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.ACCEPTED.value(),
                                                "Notificación enviada exitosamente"), HttpStatus.ACCEPTED);
                        } else {
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.NOT_FOUND.value(),
                                                "No se encontraron usuarios con roles relevantes"),
                                                HttpStatus.NOT_FOUND);
                        }

                } catch (Exception e) {
                        return new ResponseEntity<>(
                                        new PushNotificationResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                        "Error al enviar notificación: " + e.getMessage()),
                                        HttpStatus.INTERNAL_SERVER_ERROR);
                }
        }

        @PostMapping("/notification/venta-transferencia/{ventaId}/{sucursalId}/{valorTotal}")
        public ResponseEntity<PushNotificationResponse> sendVentaTransferenciaNotification(
                        @PathVariable Long ventaId,
                        @PathVariable Long sucursalId,
                        @PathVariable Double valorTotal,
                        @RequestParam(required = false) String usuarioNombre,
                        @RequestParam(required = false) String sucursalNombre) {
                try {
                        if (sucursalNombre == null || sucursalNombre.isEmpty()) {
                                Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
                                sucursalNombre = sucursal != null ? sucursal.getNombre() : "";
                        }

                        List<String> rolesRelevantes = notificationRoleService.getRolesForVentaTransferencia();
                        List<Long> usuariosRelevantes = notificationRoleService.getUserIdsByRoles(rolesRelevantes);

                        if (!usuariosRelevantes.isEmpty()) {
                                PushNotificationRequest request = notificationTemplateService
                                                .ventaTransferenciaRealizada(ventaId, sucursalId, valorTotal, sucursalNombre, usuarioNombre, df);
                                request.setUsuarioIds(usuariosRelevantes);
                                pushNotificationService.sendPushNotificationToToken(request);
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.ACCEPTED.value(),
                                                "Notificación enviada exitosamente"), HttpStatus.ACCEPTED);
                        } else {
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.NOT_FOUND.value(),
                                                "No se encontraron usuarios con roles relevantes"),
                                                HttpStatus.NOT_FOUND);
                        }

                } catch (Exception e) {
                        return new ResponseEntity<>(
                                        new PushNotificationResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                        "Error al enviar notificación: " + e.getMessage()),
                                        HttpStatus.INTERNAL_SERVER_ERROR);
                }
        }

        @PostMapping("/notification/venta-stock-critico")
        public ResponseEntity<PushNotificationResponse> sendVentaStockCriticoNotification(
                        @RequestBody VentaStockCriticoNotificationRequest body) {
                try {
                        if (body == null || body.getVentaId() == null || body.getSucursalId() == null
                                        || body.getItems() == null || body.getItems().isEmpty()) {
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.BAD_REQUEST.value(),
                                                "Parámetros insuficientes para notificación de stock crítico"),
                                                HttpStatus.BAD_REQUEST);
                        }

                        List<String> rolesRelevantes = notificationRoleService.getRolesForVentaStockCritico();
                        List<Long> usuariosRelevantes = notificationRoleService.getUserIdsByRoles(rolesRelevantes);

                        if (!usuariosRelevantes.isEmpty()) {
                                PushNotificationRequest request = notificationTemplateService.ventaStockCritico(
                                                body.getVentaId(),
                                                body.getSucursalId(),
                                                body.getSucursalNombre(),
                                                body.getUsuarioNombre(),
                                                body.getItems(),
                                                df);
                                request.setUsuarioIds(usuariosRelevantes.stream().distinct().collect(Collectors.toList()));
                                pushNotificationService.sendPushNotificationToToken(request);
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.ACCEPTED.value(),
                                                "Notificación de stock crítico enviada exitosamente"),
                                                HttpStatus.ACCEPTED);
                        } else {
                                return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.NOT_FOUND.value(),
                                                "No se encontraron usuarios con roles relevantes"),
                                                HttpStatus.NOT_FOUND);
                        }
                } catch (Exception e) {
                        return new ResponseEntity<>(
                                        new PushNotificationResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                        "Error al enviar notificación: " + e.getMessage()),
                                        HttpStatus.INTERNAL_SERVER_ERROR);
                }
        }

}