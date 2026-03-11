package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.graphql.productos.input.PrecioPorSucursalInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.*;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PrecioPorSucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PrecioPorSucursalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private TipoPrecioService tipoPrecioService;

    @SuppressWarnings("unused")
    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private com.franco.dev.fmc.service.NotificationTemplateService notificationTemplateService;

    @Autowired
    private com.franco.dev.fmc.service.PushNotificationService pushNotificationService;

    @Autowired
    private com.franco.dev.fmc.service.NotificationRoleService notificationRoleService;

    private static final java.text.DecimalFormat df = new java.text.DecimalFormat("#,###.##");

    @Autowired
    private Environment env;

    @SuppressWarnings("unused")
    @Autowired
    private PropagacionService propagacionService;

    public Optional<PrecioPorSucursal> precioPorSucursal(Long id) {
        return service.findById(id);
    }

    public List<PrecioPorSucursal> precioPorSucursalPorPresentacionId(Long id) {
        return service.findByPresentacionId(id);
    }

    public List<PrecioPorSucursal> preciosPorSucursalPorSucursalId(Long id) {
        return service.findBySucursalId(id);
    }

    public List<PrecioPorSucursal> preciosPorSucursal(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Autowired
    private org.springframework.context.ApplicationEventPublisher publisher;

    public PrecioPorSucursal savePrecioPorSucursal(PrecioPorSucursalInput input) {
        Double precioAnterior = null;
        if (input.getId() != null) {
            PrecioPorSucursal p = service.findById(input.getId()).orElse(null);
            if (p != null) {
                precioAnterior = p.getPrecio();
            }
        }
        ModelMapper m = new ModelMapper();
        PrecioPorSucursal e = m.map(input, PrecioPorSucursal.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getPresentacionId() != null) {
            e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        }
        if (input.getTipoPrecioId() != null) {
            e.setTipoPrecio(tipoPrecioService.findById(input.getTipoPrecioId()).orElse(null));
        }
        input.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        if (e.getId() == null && input.getPresentacionId() != null && input.getTipoPrecioId() != null
                && input.getSucursalId() != null) {
            PrecioPorSucursal existente = service.findBySucursalIdAndPresentacionIdAndTipoPrecioId(
                    input.getSucursalId(), input.getPresentacionId(), input.getTipoPrecioId());
            if (existente != null) {
                e.setId(existente.getId());
                e.setCreadoEn(existente.getCreadoEn());
                precioAnterior = existente.getPrecio();
            }
        }

        e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e = service.save(e);

        e = service.save(e);

        // publisher.publishEvent(new
        // com.franco.dev.fmc.event.PrecioPorSucursalActualizadoEvent(this, e,
        // precioAnterior));
        sendPrecioActualizadoNotification(e, precioAnterior);

        return e;
    }

    public Boolean deletePrecioPorSucursal(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countPrecioPorSucursal() {
        return service.count();
    }

    private void sendPrecioActualizadoNotification(PrecioPorSucursal precioActualizado, Double precioAnterior) {
        if (precioActualizado == null)
            return;

        try {
            if (precioActualizado.getPrecio() == null || precioActualizado.getPresentacion() == null) {
                return;
            }
            if (precioActualizado.getUsuario() != null)
                precioActualizado.getUsuario().getNickname();
            if (precioActualizado.getSucursal() != null)
                precioActualizado.getSucursal().getNombre();
            if (precioActualizado.getTipoPrecio() != null)
                precioActualizado.getTipoPrecio().getDescripcion();
            if (precioActualizado.getPresentacion() != null)
                precioActualizado.getPresentacion().getDescripcion();

            List<String> roles = notificationRoleService.getRolesForPrecioActualizado();
            List<Long> usuarioIds = notificationRoleService.getUserIdsByRoles(roles);
            if (usuarioIds.isEmpty()) {
                return;
            }

            com.franco.dev.domain.productos.Producto producto = obtenerProducto(precioActualizado);
            if (producto == null) {
                return;
            }

            com.franco.dev.fmc.model.PushNotificationRequest request = notificationTemplateService
                    .precioVentaActualizado(
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
            e.printStackTrace();
        }
    }

    private com.franco.dev.domain.productos.Producto obtenerProducto(PrecioPorSucursal precio) {
        if (precio == null || precio.getPresentacion() == null) {
            return null;
        }
        com.franco.dev.domain.productos.Producto producto = precio.getPresentacion().getProducto();
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