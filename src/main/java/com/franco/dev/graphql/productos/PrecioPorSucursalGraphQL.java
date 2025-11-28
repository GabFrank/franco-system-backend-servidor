package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationRoleService;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.graphql.productos.input.PrecioPorSucursalInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.*;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;

@Component
public class PrecioPorSucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(PrecioPorSucursalGraphQL.class);
    private static final DecimalFormat df = new DecimalFormat("#,###.##");

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
    private Environment env;

    @SuppressWarnings("unused")
    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private NotificationRoleService notificationRoleService;

    @Autowired
    private NotificationTemplateService notificationTemplateService;

    @Autowired
    private PushNotificationService pushNotificationService;

    public Optional<PrecioPorSucursal> precioPorSucursal(Long id) {return service.findById(id);}

    public List<PrecioPorSucursal> precioPorSucursalPorPresentacionId(Long id) {return service.findByPresentacionId(id);}

    public List<PrecioPorSucursal> preciosPorSucursalPorSucursalId(Long id){ return service.findBySucursalId(id);}

    public List<PrecioPorSucursal> preciosPorSucursal(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public PrecioPorSucursal savePrecioPorSucursal(PrecioPorSucursalInput input){
        PrecioPorSucursal precioAnterior = null;
        if (input.getId() != null) {
            precioAnterior = service.findById(input.getId()).orElse(null);
        }
        ModelMapper m = new ModelMapper();
        PrecioPorSucursal e = m.map(input, PrecioPorSucursal.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getPresentacionId()!=null){
            e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        }
        if(input.getTipoPrecioId()!=null){
            e.setTipoPrecio(tipoPrecioService.findById(input.getTipoPrecioId()).orElse(null));
        }
        input.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e = service.save(e);
        enviarNotificacionPrecioActualizado(e, precioAnterior);
        return e;
    }

    public Boolean deletePrecioPorSucursal(Long id){
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countPrecioPorSucursal(){
        return service.count();
    }

    private void enviarNotificacionPrecioActualizado(PrecioPorSucursal precioActualizado, PrecioPorSucursal precioAnterior) {
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
                    precioAnterior != null ? precioAnterior.getPrecio() : null,
                    precioActualizado.getUsuario(),
                    precioActualizado.getSucursal(),
                    df);
            if (request == null) {
                return;
            }
            request.setUsuarioIds(usuarioIds);
            pushNotificationService.sendPushNotificationToToken(request);
        } catch (Exception e) {
            log.error("Error al enviar notificación de precio actualizado: {}", e.getMessage(), e);
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
            log.warn("No se pudo obtener el producto para la presentación {}: {}", precio.getPresentacion().getId(), e.getMessage());
            return null;
        }
    }

}
