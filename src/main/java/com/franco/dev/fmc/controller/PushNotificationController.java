package com.franco.dev.fmc.controller;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.model.PushNotificationResponse;
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
        private VentaCreditoService ventaCreditoService;

        @Autowired
        private NotificationRoleService notificationRoleService;

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

        @PostMapping("/notification/venta-credito/{ventaCreditoId}/{sucursalId}/{personaId}/{valorTotal}")
        public ResponseEntity<PushNotificationResponse> sendVentaCreditoNotification(
                        @PathVariable Long ventaCreditoId,
                        @PathVariable Long sucursalId,
                        @PathVariable Long personaId,
                        @PathVariable Double valorTotal) {
                try {
                        Sucursal sucursal = sucursalService.findById(sucursalId).orElse(null);
                        VentaCredito ventaCreditoTemp = new VentaCredito();
                        ventaCreditoTemp.setId(ventaCreditoId);
                        ventaCreditoTemp.setSucursalId(sucursalId);
                        ventaCreditoTemp.setValorTotal(valorTotal);

                        List<String> rolesRelevantes = notificationRoleService.getRolesForVentaCredito();
                        List<Long> usuariosRelevantes = notificationRoleService.getUserIdsByRoles(rolesRelevantes);

                        if (!usuariosRelevantes.isEmpty()) {
                                PushNotificationRequest requestAdmin = notificationTemplateService
                                                .ventaCreditoRealizada(ventaCreditoTemp, sucursal, df);
                                requestAdmin.setType("VENTA_CREDITO_ADMIN");
                                requestAdmin.setUsuarioIds(usuariosRelevantes);
                                pushNotificationService.sendPushNotificationToToken(requestAdmin);
                        }

                        Usuario usuarioCliente = usuarioService.findByPersonaId(personaId);
                        if (usuarioCliente != null) {
                                PushNotificationRequest requestCliente = notificationTemplateService
                                                .ventaCreditoRealizadaCliente(ventaCreditoTemp, sucursal, df);
                                requestCliente.setUsuarioIds(Collections.singletonList(usuarioCliente.getId()));
                                pushNotificationService.sendPushNotificationToToken(requestCliente);
                        }

                        return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.ACCEPTED.value(),
                                        "Notificaciones enviadas exitosamente"), HttpStatus.ACCEPTED);

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

}