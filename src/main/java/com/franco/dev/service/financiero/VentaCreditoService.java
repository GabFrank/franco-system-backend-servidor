package com.franco.dev.service.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.repository.financiero.VentaCreditoRepositoryImpl;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.configuracion.NotificacionPreferenciaService;
import java.util.stream.Collectors;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaCreditoService extends CrudService<VentaCredito, VentaCreditoRepository, EmbebedPrimaryKey> {

    public static final DecimalFormat df = new DecimalFormat("#,###.##");

    private VentaCreditoRepository repository = null;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    private VentaService ventaService;

    @Autowired
    private MultiTenantService multiTenantService;
    @Autowired
    private PushNotificationService pushNotificationService;
    @Autowired
    private NotificationTemplateService notificationTemplateService;

    @Autowired
    private NotificacionPreferenciaService preferenciaService;

    @Autowired
    private VentaCreditoRepositoryImpl ventaCreditoRepository;

    @Autowired
    public VentaCreditoService(@Lazy VentaService ventaService, VentaCreditoRepository repository) {
        this.ventaService = ventaService;
        this.repository = repository;
    }

    @Override
    public VentaCreditoRepository getRepository() {
        return repository;
    }

    public List<VentaCredito> findByClienteAndVencimiento(Long id, LocalDateTime inicio, LocalDateTime fin) {
        return repository.findAllByClienteIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(id,
                inicio, fin);
    }

    public Page<VentaCredito> findByClienteId(Long id, EstadoVentaCredito estado, Pageable pageable) {
        return repository.findAllByClienteIdAndEstadoOrderByCreadoEnDesc(id, estado, pageable);
    }

    public List<VentaCredito> findWithFilters(Long id, LocalDateTime fechaInicio, LocalDateTime fechaFin,
            EstadoVentaCredito estado, Boolean cobro) {
        if (fechaInicio != null && fechaFin != null) {
            return repository.findAllWithDateAndFilters(id, fechaInicio, fechaFin, estado, cobro);
        } else {
            return repository.findAllWithFilters(id, estado);
        }
    }

    public List<VentaCredito> findByClienteId(Long id, EstadoVentaCredito estado) {
        return repository.findAllByClienteIdAndEstadoOrderByCreadoEnDesc(id, estado);
    }

    public Long countByClienteIdAndEstado(Long id, EstadoVentaCredito estado) {
        return repository.countByClienteIdAndEstado(id, estado);
    }

    public VentaCredito findByVentaIdAndSucId(Long id, Long sucId) {
        return repository.findByVentaIdAndSucursalId(id, sucId);
    }

    @Override
    public VentaCredito save(VentaCredito entity) {

        VentaCredito saved = super.save(entity);

        if (saved.getCliente() != null && saved.getCliente().getPersona() != null) {

            Usuario usuario = usuarioService.findByPersonaId(saved.getCliente().getPersona().getId());

            if (usuario != null) {
                try {
                    Sucursal sucursal = sucursalService.findById(saved.getSucursalId()).orElse(null);

                    // 1. Notificacion al Cliente (Compra a credito) - Solo al usuario especifico
                    PushNotificationRequest requestCliente = notificationTemplateService
                            .ventaCreditoRealizadaCliente(saved, sucursal, df);
                    requestCliente.setUsuarioIds(Collections.singletonList(usuario.getId()));
                    pushNotificationService.sendPushNotificationToToken(requestCliente);

                    // 2. Notificacion a Roles Administrativos (Venta a credito) - A todo el mundo
                    // con rol
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

                }
            }
        }

        return saved;
    }

    public Boolean cancelarVentaCredito(Long id, Long sucId, Venta venta) {
        VentaCredito ventaCredito = findById(new EmbebedPrimaryKey(id, sucId)).orElse(null);
        if (ventaCredito != null) {
            try {
                if (venta == null) {
                    venta = ventaService.findById(new EmbebedPrimaryKey(ventaCredito.getVenta().getId(), sucId))
                            .orElse(null);
                    if (venta.getEstado() != VentaEstado.CANCELADA) {
                        venta.setEstado(VentaEstado.CANCELADA);
                        venta = ventaService.save(venta);
                        ventaCredito.setEstado(EstadoVentaCredito.CANCELADO);
                    } else {
                        venta.setEstado(VentaEstado.CONCLUIDA);
                        venta = ventaService.save(venta);
                        ventaCredito.setEstado(EstadoVentaCredito.ABIERTO);
                    }
                } else {
                    if (venta.getEstado() == VentaEstado.CANCELADA) {
                        ventaCredito.setEstado(EstadoVentaCredito.CANCELADO);
                    } else {
                        ventaCredito.setEstado(EstadoVentaCredito.ABIERTO);
                    }
                }
                this.save(ventaCredito);
                return true;
            } catch (Exception e) {
                throw new GraphQLException("No se puedo cancelar la venta");
            }
        } else {
            throw new GraphQLException("Venta credito no encontrada");
        }
    }

    public VentaCredito findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

}