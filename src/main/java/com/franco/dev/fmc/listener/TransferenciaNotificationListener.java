package com.franco.dev.fmc.listener;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.fmc.event.TransferenciaCambioSucursalEvent;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationRoleService;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.repository.empresarial.SucursalRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.List;

@Component
@AllArgsConstructor
public class TransferenciaNotificationListener {

    private final PushNotificationService pushNotificationService;
    private final NotificationTemplateService notificationTemplateService;
    private final NotificationRoleService notificationRoleService;
    private final com.franco.dev.repository.operaciones.TransferenciaRepository transferenciaRepository;
    private final SucursalRepository sucursalRepository;

    @org.springframework.scheduling.annotation.Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onTransferenciaCambioSucursal(TransferenciaCambioSucursalEvent event) {
        Long transferenciaId = event.getTransferencia().getId();
        Transferencia newTransferencia = transferenciaRepository.findById(transferenciaId).orElse(null);

        if (newTransferencia == null)
            return;

        Sucursal oldSucursalOrigen = event.getOldSucursalOrigen();
        if (oldSucursalOrigen != null) {
            oldSucursalOrigen = sucursalRepository.findById(oldSucursalOrigen.getId()).orElse(null);
        }

        Sucursal oldSucursalDestino = event.getOldSucursalDestino();
        if (oldSucursalDestino != null) {
            oldSucursalDestino = sucursalRepository.findById(oldSucursalDestino.getId()).orElse(null);
        }

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

    @org.springframework.scheduling.annotation.Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onTransferenciaIniciada(com.franco.dev.fmc.event.TransferenciaIniciadaEvent event) {
        try {
            Long transferenciaId = event.getTransferencia().getId();
            Transferencia transferencia = transferenciaRepository.findById(transferenciaId).orElse(null);

            if (transferencia != null) {
                // Initialize Lazy Objects safely within Transaction
                if (transferencia.getSucursalOrigen() != null)
                    transferencia.getSucursalOrigen().getNombre();
                if (transferencia.getSucursalDestino() != null)
                    transferencia.getSucursalDestino().getNombre();
                sendTransferenciaIniciadaNotification(transferencia);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendTransferenciaIniciadaNotification(Transferencia transferencia) {
        if (transferencia == null)
            return;

        try {
            List<String> roles = notificationRoleService.getRolesForTransferenciaIniciada();
            List<Long> usuarioIds = notificationRoleService.getUserIdsByRoles(roles);

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
