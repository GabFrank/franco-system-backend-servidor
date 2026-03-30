package com.franco.dev.fmc.listener;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.fmc.event.InicioSesionCreadoEvent;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.repository.configuracion.InicioSesionRepository;
import com.franco.dev.service.empresarial.SucursalService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@AllArgsConstructor
public class SeguridadNotificationListener {

    private final PushNotificationService pushNotificationService;
    private final NotificationTemplateService notificationTemplateService;
    private final SucursalService sucursalService;
    private final InicioSesionRepository inicioSesionRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInicioSesionCreado(InicioSesionCreadoEvent event) {
        InicioSesion sesion = event.getInicioSesion();
        detectarYNotificarNuevoDispositivo(sesion);
    }

    private void detectarYNotificarNuevoDispositivo(InicioSesion sesion) {
        if (sesion == null) {
            return;
        }

        if (sesion.getUsuario() == null) {
            return;
        }

        try {
            boolean esDispositivoNuevo = sesion.getIdDispositivo() != null &&
                    !inicioSesionRepository.existsByUsuarioIdAndIdDispositivo(
                            sesion.getUsuario().getId(), sesion.getIdDispositivo());

            String nombreSucursal = null;
            if (sesion.getSucursal() != null && sucursalService != null) {
                com.franco.dev.domain.empresarial.Sucursal sucursal = sucursalService
                        .findById(sesion.getSucursal().getId()).orElse(null);
                if (sucursal != null) {
                    nombreSucursal = sucursal.getNombre();
                }
            }
            if (esDispositivoNuevo) {
                String tipoDispositivo = sesion.getTipoDespositivo() != null
                        ? sesion.getTipoDespositivo().toString()
                        : "DESCONOCIDO";

                com.franco.dev.fmc.model.PushNotificationRequest requestSeguridad = notificationTemplateService
                        .nuevoDispositivoDetectado(tipoDispositivo, nombreSucursal);

                requestSeguridad.setUsuarioIds(java.util.Collections.singletonList(sesion.getUsuario().getId()));
                pushNotificationService.sendPushNotificationToToken(requestSeguridad);
            }

        } catch (Exception e) {
            // Loguear error pero no interrumpir flujo
            e.printStackTrace();
        }
    }
}
