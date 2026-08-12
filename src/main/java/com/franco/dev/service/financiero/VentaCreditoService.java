package com.franco.dev.service.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.repository.financiero.VentaCreditoCuotaRepository;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.repository.financiero.VentaCreditoRepositoryImpl;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.operaciones.VentaService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaCreditoService extends CrudService<VentaCredito, VentaCreditoRepository, EmbebedPrimaryKey> {

    public static final DecimalFormat df = new DecimalFormat("#,###.##");

    private VentaCreditoRepository repository = null;

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
    private VentaCreditoCuotaRepository ventaCreditoCuotaRepository;

    @Autowired
    private ApplicationEventPublisher publisher2;

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
        return super.save(entity);
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

    // =====================================================================
    // Cobro / finalizacion (reusado por el cobro manual y por RRHH planilla)
    // =====================================================================

    /**
     * Marca una venta a credito como cobrada/cerrada: FINALIZADO + fechaCobro.
     * Idempotente (si ya esta FINALIZADO, no hace nada). No crea Cobro ni movimiento
     * de caja — ese asiento formal lo hara el modulo de Tesoreria.
     */
    public VentaCredito finalizarPorCobro(VentaCredito vc) {
        if (vc == null) return null;
        if (vc.getEstado() == EstadoVentaCredito.FINALIZADO) return vc;
        vc.setEstado(EstadoVentaCredito.FINALIZADO);
        vc.setFechaCobro(LocalDateTime.now());
        return this.save(vc);
    }

    /** Revierte la finalizacion: restaura el estado previo y limpia fechaCobro. */
    public VentaCredito revertirFinalizacion(VentaCredito vc, EstadoVentaCredito estadoPrevio) {
        if (vc == null) return null;
        vc.setEstado(estadoPrevio != null ? estadoPrevio : EstadoVentaCredito.ABIERTO);
        vc.setFechaCobro(null);
        return this.save(vc);
    }

    /**
     * Cuotas impagas (cobro == null, activo) de las ventas a credito del cliente en
     * estados cobrables (ABIERTO/EN_MORA/INCOBRABLE). Si `hasta` != null, solo las
     * vencidas hasta esa fecha (uso mensual); si es null, todas (uso finiquito).
     * Ordenadas por vencimiento ascendente (mas vieja primero).
     */
    public List<VentaCreditoCuota> cuotasImpagasDeCliente(Long clienteId, LocalDateTime hasta) {
        List<VentaCreditoCuota> out = new ArrayList<>();
        if (clienteId == null) return out;
        for (EstadoVentaCredito est : new EstadoVentaCredito[]{
                EstadoVentaCredito.ABIERTO, EstadoVentaCredito.EN_MORA, EstadoVentaCredito.INCOBRABLE}) {
            for (VentaCredito vc : repository.findAllByClienteIdAndEstadoOrderByCreadoEnDesc(clienteId, est)) {
                for (VentaCreditoCuota c : ventaCreditoCuotaRepository
                        .findAllByVentaCreditoIdAndSucursalId(vc.getId(), vc.getSucursalId())) {
                    if (c.getCobro() != null) continue;                          // ya cobrada (POS)
                    if (Boolean.FALSE.equals(c.getActivo())) continue;
                    if (hasta != null && (c.getVencimiento() == null || c.getVencimiento().isAfter(hasta))) continue;
                    out.add(c);
                }
            }
        }
        out.sort(Comparator.comparing(VentaCreditoCuota::getVencimiento,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    /** Todas las cuotas de una venta a credito (por su PK compuesta). */
    public List<VentaCreditoCuota> cuotasDeVenta(VentaCredito vc) {
        if (vc == null) return new ArrayList<>();
        return ventaCreditoCuotaRepository.findAllByVentaCreditoIdAndSucursalId(vc.getId(), vc.getSucursalId());
    }

    /** Una cuota por su PK compuesta (id + sucursal). */
    public VentaCreditoCuota cuota(Long id, Long sucId) {
        if (id == null || sucId == null) return null;
        return ventaCreditoCuotaRepository.findById(new EmbebedPrimaryKey(id, sucId)).orElse(null);
    }

}