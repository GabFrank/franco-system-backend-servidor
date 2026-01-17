package com.franco.dev.fmc.listener;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.fmc.event.PrecioPorSucursalActualizadoEvent;
import com.franco.dev.fmc.event.ProductoCreadoEvent;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationRoleService;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.productos.PresentacionService;
import lombok.AllArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
public class ProductosNotificationListener {

    private final PushNotificationService pushNotificationService;
    private final NotificationTemplateService notificationTemplateService;
    private final NotificationRoleService notificationRoleService;
    private final SucursalService sucursalService;
    private final Environment env;
    private final PresentacionService presentacionService;

    private static final DecimalFormat df = new DecimalFormat("#,###.##");

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductoCreado(ProductoCreadoEvent event) {
        Producto producto = event.getProducto();
        if (producto == null) {
            return;
        }

        try {
            List<String> roles = notificationRoleService.getRolesForProductoCreado();
            List<Long> usuarioIds = notificationRoleService.getUserIdsByRoles(roles);

            if (usuarioIds.isEmpty()) {
                return;
            }
            Sucursal sucursal = null;
            // Intentar obtener sucursal del enviroment si está disponible
            if (env != null && sucursalService != null) {
                try {
                    String sucursalIdStr = env.getProperty("sucursalId");
                    if (sucursalIdStr != null) {
                        Long sucursalId = Long.valueOf(sucursalIdStr);
                        Optional<Sucursal> optSuc = sucursalService.findById(sucursalId);
                        if (optSuc != null && optSuc.isPresent()) {
                            sucursal = optSuc.get();
                        }
                    }
                } catch (Exception e) {
                }
            }
            PushNotificationRequest request = notificationTemplateService.productoCreado(producto, sucursal);
            if (request != null) {
                request.setUsuarioIds(usuarioIds);
                pushNotificationService.sendPushNotificationToToken(request);
            }
        } catch (Exception e) {
            // Silent notification error
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPrecioPorSucursalActualizado(PrecioPorSucursalActualizadoEvent event) {
        PrecioPorSucursal precioActualizado = event.getPrecioPorSucursal();
        Double precioAnterior = event.getPrecioAnterior();

        if (precioActualizado == null || precioActualizado.getPrecio() == null
                || precioActualizado.getPresentacion() == null) {
            return;
        }
        try {
            List<String> roles = notificationRoleService.getRolesForPrecioActualizado();
            List<Long> usuarioIds = notificationRoleService.getUserIdsByRoles(roles);
            if (usuarioIds.isEmpty()) {
                return;
            }

            Producto producto = obtenerProducto(precioActualizado);
            if (producto == null) {
                return;
            }

            PushNotificationRequest request = notificationTemplateService.precioVentaActualizado(
                    producto,
                    precioActualizado.getPresentacion(),
                    precioActualizado.getTipoPrecio(),
                    precioActualizado.getPrecio(),
                    precioAnterior,
                    precioActualizado.getUsuario(),
                    precioActualizado.getSucursal(),
                    df);
            if (request == null) {
                return;
            }
            request.setUsuarioIds(usuarioIds);
            pushNotificationService.sendPushNotificationToToken(request);
        } catch (Exception e) {
            // Silent notification error
        }
    }

    private Producto obtenerProducto(PrecioPorSucursal precio) {
        if (precio == null || precio.getPresentacion() == null) {
            return null;
        }
        Producto producto = precio.getPresentacion().getProducto();
        if (producto != null) {
            return producto;
        }
        try {
            return presentacionService.findById(precio.getPresentacion().getId())
                    .map(p -> p.getProducto())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
