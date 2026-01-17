package com.franco.dev.fmc.listener;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.fmc.event.TransferenciaCambioSucursalEvent;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationRoleService;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.franco.dev.domain.personas.Usuario;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.List;

@Component
@AllArgsConstructor
public class TransferenciaNotificationListener {

    private final PushNotificationService pushNotificationService;
    private final NotificationTemplateService notificationTemplateService;
    private final NotificationRoleService notificationRoleService;
    private final com.franco.dev.repository.personas.UsuarioRepository usuarioRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferenciaCambioSucursal(TransferenciaCambioSucursalEvent event) {
        Transferencia newTransferencia = event.getTransferencia();
        Sucursal oldSucursalOrigen = event.getOldSucursalOrigen();
        Sucursal oldSucursalDestino = event.getOldSucursalDestino();

        try {
            // Verificar cambio en Sucursal Origen
            if (oldSucursalOrigen != null && newTransferencia.getSucursalOrigen() != null
                    && !oldSucursalOrigen.getId()
                            .equals(newTransferencia.getSucursalOrigen().getId())) {
                sendCambioSucursalNotification(newTransferencia, oldSucursalOrigen,
                        newTransferencia.getSucursalOrigen(), true);
            }

            // Verificar cambio en Sucursal Destino
            if (oldSucursalDestino != null && newTransferencia.getSucursalDestino() != null
                    && !oldSucursalDestino.getId()
                            .equals(newTransferencia.getSucursalDestino().getId())) {
                sendCambioSucursalNotification(newTransferencia, oldSucursalDestino,
                        newTransferencia.getSucursalDestino(), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendCambioSucursalNotification(Transferencia transferencia,
            Sucursal oldSucursal,
            Sucursal newSucursal, boolean isOrigen) {
        List<String> roles = notificationRoleService.getRolesForCambioSucursalPreTransferencia();
        List<Long> userIds = notificationRoleService.getUserIdsByRoles(roles);

        if (userIds.isEmpty())
            return;

        PushNotificationRequest request = notificationTemplateService
                .cambioSucursalPreTransferencia(
                        transferencia,
                        oldSucursal,
                        newSucursal,
                        isOrigen);

        if (request != null) {
            request.setUsuarioIds(userIds);
            pushNotificationService.sendPushNotificationToToken(request);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferenciaIniciada(com.franco.dev.fmc.event.TransferenciaIniciadaEvent event) {
        try {
            Transferencia transferencia = event.getTransferencia();
            sendTransferenciaIniciadaNotification(transferencia);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendTransferenciaIniciadaNotification(Transferencia transferencia) {
        if (transferencia == null)
            return;

        try {
            List<Long> usuarioIds = usuarioRepository.findAll()
                    .stream()
                    .map(Usuario::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (usuarioIds.isEmpty()) {
                return;
            }
            Sucursal sucursalOrigen = transferencia.getSucursalOrigen();
            Sucursal sucursalDestino = transferencia.getSucursalDestino();

            PushNotificationRequest request = notificationTemplateService.transferenciaIniciada(
                    transferencia, sucursalOrigen, sucursalDestino);

            if (request != null) {
                request.setUsuarioIds(usuarioIds);
                pushNotificationService.sendPushNotificationToToken(request);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
