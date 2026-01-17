package com.franco.dev.fmc.listener;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.event.CotizacionActualizadaEvent;
import com.franco.dev.fmc.event.GastoRealizadoEvent;
import com.franco.dev.fmc.event.RetiroRealizadoEvent;
import com.franco.dev.fmc.event.VentaCreditoRealizadaEvent;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.configuracion.NotificacionPreferenciaService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class FinancieroNotificationListener {

    private final PushNotificationService pushNotificationService;
    private final NotificationTemplateService notificationTemplateService;
    private final NotificacionPreferenciaService preferenciaService;
    private final UsuarioService usuarioService;
    private final SucursalService sucursalService;

    public static final DecimalFormat df = new DecimalFormat("#,###.##");

    private final InicioSesionService inicioSesionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGastoRealizado(GastoRealizadoEvent event) {
        Gasto gasto = event.getGasto();
        if (gasto.getResponsable() != null && gasto.getResponsable().getPersona() != null) {
            Usuario usuario = usuarioService.findByPersonaId(gasto.getResponsable().getPersona().getId());
            if (usuario != null) {
                try {
                    Sucursal sucursal = sucursalService.findById(gasto.getSucursalId()).orElse(null);
                    PushNotificationRequest request = notificationTemplateService.gastoRealizado(gasto, sucursal, df);
                    request.setUsuarioIds(Collections.singletonList(usuario.getId()));
                    pushNotificationService.sendPushNotificationToToken(request);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRetiroRealizado(RetiroRealizadoEvent event) {
        Retiro e = event.getRetiro();
        if (e.getResponsable() != null && e.getResponsable().getPersona() != null) {
            try {
                Sucursal sucursal = sucursalService.findById(e.getSucursalId()).orElse(null);

                List<Usuario> usuariosDestino = preferenciaService.obtenerUsuariosPorTipoNotificacion("RETIRO");

                if (!usuariosDestino.isEmpty()) {
                    PushNotificationRequest request = notificationTemplateService.retiroRealizado(e, sucursal);
                    List<Long> ids = usuariosDestino.stream().map(Usuario::getId)
                            .collect(Collectors.toList());
                    request.setUsuarioIds(ids);
                    pushNotificationService.sendPushNotificationToToken(request);
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVentaCreditoRealizada(VentaCreditoRealizadaEvent event) {
        VentaCredito saved = event.getVentaCredito();
        if (saved.getCliente() != null && saved.getCliente().getPersona() != null) {

            Usuario usuario = usuarioService.findByPersonaId(saved.getCliente().getPersona().getId());

            if (usuario != null) {
                try {
                    Sucursal sucursal = sucursalService.findById(saved.getSucursalId()).orElse(null);
                    PushNotificationRequest requestCliente = notificationTemplateService
                            .ventaCreditoRealizadaCliente(saved, sucursal, df);
                    requestCliente.setUsuarioIds(Collections.singletonList(usuario.getId()));
                    pushNotificationService.sendPushNotificationToToken(requestCliente);
                    List<Usuario> usuariosAdmin = preferenciaService
                            .obtenerUsuariosPorTipoNotificacion("VENTA_CREDITO");
                    if (!usuariosAdmin.isEmpty()) {
                        PushNotificationRequest requestAdmin = notificationTemplateService.ventaCreditoRealizada(saved,
                                sucursal, df);
                        List<Long> adminIds = usuariosAdmin.stream().map(Usuario::getId).collect(Collectors.toList());
                        requestAdmin.setUsuarioIds(adminIds);
                        pushNotificationService.sendPushNotificationToToken(requestAdmin);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCotizacionActualizada(CotizacionActualizadaEvent event) {
        Cambio cambio = event.getCambio();
        if (cambio == null || cambio.getMoneda() == null) {
            return;
        }

        try {
            Moneda moneda = cambio.getMoneda();
            String denominacion = moneda.getDenominacion() != null ? moneda.getDenominacion() : "Moneda";
            String simbolo = moneda.getSimbolo() != null ? moneda.getSimbolo() : "";

            PushNotificationRequest request = notificationTemplateService.cotizacionActualizada(
                    denominacion,
                    simbolo,
                    cambio.getValorEnGs());

            if (request == null) {
                return;
            }
            List<com.franco.dev.domain.configuracion.InicioSesion> sesionesActivas = inicioSesionService
                    .findSessionsWithValidTokens();

            List<Long> usuariosIds = sesionesActivas.stream()
                    .filter(s -> s.getUsuario() != null)
                    .map(s -> s.getUsuario().getId())
                    .distinct()
                    .collect(Collectors.toList());

            if (!usuariosIds.isEmpty()) {
                request.setUsuarioIds(usuariosIds);
                pushNotificationService.sendPushNotificationToToken(request);
            }

        } catch (Exception e) {
        }
    }
}
