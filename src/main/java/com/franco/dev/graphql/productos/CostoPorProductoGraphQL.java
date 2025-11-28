package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationRoleService;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.graphql.productos.input.CostoPorProductoInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class CostoPorProductoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(CostoPorProductoGraphQL.class);
    private static final DecimalFormat df = new DecimalFormat("#,###.##");

    @Autowired
    private CostosPorProductoService service;

    @SuppressWarnings("unused")
    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private NotificationRoleService notificationRoleService;

    @Autowired
    private NotificationTemplateService notificationTemplateService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private Environment env;
    public Page<CostoPorProducto> costosPorProductoId(Long id, int page, int size) {
        Pageable pageable = PageRequest.of(page,size);
        return service.findByProductoId(id, pageable);
    }

    public CostoPorProducto saveCostoPorProducto(CostoPorProductoInput input){
        if(input.getId()==null) input.setCreadoEn(LocalDateTime.now());
        CostoPorProducto referenciaAnterior = obtenerReferenciaAnterior(input);
        ModelMapper m = new ModelMapper();
        CostoPorProducto e = m.map(input, CostoPorProducto.class);
        e = service.save(e);
        enviarNotificacionAjusteCosto(input, e, referenciaAnterior);
        return e;
    }

    public Boolean deleteCostoPorProducto(Long id){
        return service.deleteById(id);
    }

    private CostoPorProducto obtenerReferenciaAnterior(CostoPorProductoInput input) {
        try {
            if (input.getId() != null) {
                Optional<CostoPorProducto> existente = service.findById(input.getId());
                return existente.orElse(null);
            }
            if (input.getProductoId() != null) {
                return service.findLastByProductoId(input.getProductoId());
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener referencia previa de costo: {}", e.getMessage());
        }
        return null;
    }

    private void enviarNotificacionAjusteCosto(
            CostoPorProductoInput input,
            CostoPorProducto saved,
            CostoPorProducto referenciaAnterior) {
        if (saved == null) {
            return;
        }
        try {
            Producto producto = resolverProducto(input, saved);
            Double nuevoCosto = saved.getCostoMedio() != null ? saved.getCostoMedio() : input.getCostoMedio();
            if (producto == null || nuevoCosto == null) {
                return;
            }

            List<String> roles = notificationRoleService.getRolesForAjusteCosto();
            List<Long> usuarioIds = notificationRoleService.getUserIdsByRoles(roles);
            if (usuarioIds.isEmpty()) {
                return;
            }

            Double costoAnterior = referenciaAnterior != null ? referenciaAnterior.getCostoMedio() : null;
            Double ultimoPrecioCompra = saved.getUltimoPrecioCompra() != null
                    ? saved.getUltimoPrecioCompra()
                    : input.getUltimoPrecioCompra();
            Usuario usuario = resolverUsuario(input, saved);
            Sucursal sucursal = resolverSucursal(input != null ? input.getSucursalId() : null, saved.getSucursal());

            PushNotificationRequest request = notificationTemplateService.ajusteCosto(
                    producto,
                    sucursal,
                    nuevoCosto,
                    costoAnterior,
                    ultimoPrecioCompra,
                    usuario,
                    df);
            if (request == null) {
                return;
            }
            request.setUsuarioIds(usuarioIds);
            pushNotificationService.sendPushNotificationToToken(request);
        } catch (Exception e) {
            log.error("Error al enviar notificación de ajuste de costo: {}", e.getMessage(), e);
        }
    }

    private Producto resolverProducto(CostoPorProductoInput input, CostoPorProducto saved) {
        Long productoId = input != null ? input.getProductoId() : null;
        if (productoId == null && saved != null && saved.getProducto() != null) {
            productoId = saved.getProducto().getId();
            if (saved.getProducto().getDescripcion() != null) {
                return saved.getProducto();
            }
        }
        if (productoId == null) {
            return null;
        }
        return productoService.findById(productoId).orElse(saved != null ? saved.getProducto() : null);
    }

    private Usuario resolverUsuario(CostoPorProductoInput input, CostoPorProducto saved) {
        Long usuarioId = input != null ? input.getUsuarioId() : null;
        if (usuarioId == null && saved != null && saved.getUsuario() != null) {
            return saved.getUsuario();
        }
        if (usuarioId == null) {
            return null;
        }
        return usuarioService.findById(usuarioId).orElse(saved != null ? saved.getUsuario() : null);
    }

    private Sucursal resolverSucursal(Long sucursalId, Sucursal persistida) {
        if (sucursalId == null && persistida != null) {
            return persistida;
        }
        Long id = sucursalId != null ? sucursalId : obtenerSucursalPorDefecto();
        if (id == null) {
            return persistida;
        }
        return sucursalService.findById(id).orElse(persistida);
    }

    private Long obtenerSucursalPorDefecto() {
        try {
            if (env != null) {
                String sucursalIdStr = env.getProperty("sucursalId");
                if (sucursalIdStr != null) {
                    return Long.valueOf(sucursalIdStr);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
