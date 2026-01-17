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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInventarioIniciado(InventarioIniciadoEvent event) {
        Inventario inventario = event.getInventario();
        try {
            List<String> roles = Arrays.asList(
                    "ADMIN",
                    "SOPORTE",
                    "CREAR INVENTARIO",
                    "VER INVENTARIO",
                    "PARTICIPAR DEL INVENTARIO");
            List<Long> usuarioIds = notificationRoleService.getUserIdsByRoles(roles);

            if (!usuarioIds.isEmpty()) {
                String sucursalNombre = inventario.getSucursal() != null
                        ? inventario.getSucursal().getNombre()
                        : "Sucursal no especificada";
                String usuarioNombre = inventario.getUsuario() != null
                        ? inventario.getUsuario().getPersona().getNombre()
                        : "Usuario";
                String tipoInventario = inventario.getTipo() != null
                        ? inventario.getTipo().name()
                        : "";

                PushNotificationRequest request = notificationTemplateService.inventarioIniciado(
                        tipoInventario,
                        sucursalNombre,
                        usuarioNombre,
                        inventario.getId());
                request.setUsuarioIds(usuarioIds);

                pushNotificationService.sendPushNotificationToToken(request);
            }
        } catch (Exception ex) {
            // Silent error
            ex.printStackTrace();
        }
    }
}
