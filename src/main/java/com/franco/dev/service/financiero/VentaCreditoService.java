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
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Logger log = LoggerFactory.getLogger(VentaCreditoService.class);
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
        Usuario usuario = usuarioService.findByPersonaId(saved.getCliente().getPersona().getId());
        if (usuario != null) {
            Sucursal sucursal = sucursalService.findById(saved.getSucursalId()).orElse(null);
            PushNotificationRequest request = notificationTemplateService.ventaCreditoRealizada(saved, sucursal, df);
            request.setUsuarioIds(Collections.singletonList(usuario.getId()));
            pushNotificationService.sendPushNotificationToToken(request);
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