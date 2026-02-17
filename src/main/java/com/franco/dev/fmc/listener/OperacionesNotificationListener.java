package com.franco.dev.fmc.listener;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.fmc.event.InventarioIniciadoEvent;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationRoleService;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Arrays;
import java.util.List;

@Component
@AllArgsConstructor
public class OperacionesNotificationListener {

    private final PushNotificationService pushNotificationService;
    private final NotificationTemplateService notificationTemplateService;
    private final NotificationRoleService notificationRoleService;
    private final com.franco.dev.repository.operaciones.InventarioRepository inventarioRepository;
    private final com.franco.dev.repository.empresarial.SucursalRepository sucursalRepository;

    @org.springframework.scheduling.annotation.Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onInventarioIniciado(InventarioIniciadoEvent event) {
        try {
            Long inventarioId = event.getInventario().getId();
            Inventario inventario = inventarioRepository.findById(inventarioId).orElse(null);

            if (inventario != null) {
                String sucursalNombre = "Sucursal no especificada";
                if (inventario.getSucursal() != null) {
                    com.franco.dev.domain.empresarial.Sucursal s = sucursalRepository
                            .findById(inventario.getSucursal().getId()).orElse(null);
                    if (s != null)
                        sucursalNombre = s.getNombre();
                }

                String usuarioNombre = "Usuario";
                if (inventario.getUsuario() != null) {
                    try {
                        usuarioNombre = inventario.getUsuario().getPersona().getNombre();
                    } catch (Exception e) {
                        usuarioNombre = inventario.getUsuario().getNickname();
                    }
                }

                String tipoInventario = inventario.getTipo() != null ? inventario.getTipo().name() : "";

                sendInventarioIniciadoNotification(inventario, sucursalNombre, usuarioNombre, tipoInventario);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void sendInventarioIniciadoNotification(Inventario inventario, String sucursalNombre, String usuarioNombre,
            String tipoInventario) {
        try {
            List<String> roles = Arrays.asList(
                    "ADMIN",
                    "SOPORTE",
                    "CREAR INVENTARIO",
                    "VER INVENTARIO",
                    "PARTICIPAR DEL INVENTARIO");
            List<Long> usuarioIds = notificationRoleService.getUserIdsByRoles(roles);

            if (!usuarioIds.isEmpty()) {
                PushNotificationRequest request = notificationTemplateService.inventarioIniciado(
                        tipoInventario,
                        sucursalNombre,
                        usuarioNombre,
                        inventario.getId());
                request.setUsuarioIds(usuarioIds);

                pushNotificationService.sendPushNotificationToToken(request);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
