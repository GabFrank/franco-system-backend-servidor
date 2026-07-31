package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.repository.financiero.RetiroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Puente Retiro (caja PDV) → Caja Mayor. El retiro nace en la filial y llega a
 * central por replicación lógica de PostgreSQL (BRANCH_TO_MAIN), sin pasar por
 * Spring — por eso la integración es un poller reconciliador, no un evento/hook
 * (mismo patrón que SIFEN/cotización). Por cada retiro destinado a una caja mayor
 * y aún no posteado, agrupa el efectivo por moneda y postea un INGRESO, marcando
 * la fila para no duplicar (idempotente + guard anti doble-ingreso).
 *
 * Desactivado por defecto ({@code matchIfMissing=false}), se habilita por instancia.
 */
@Component
@ConditionalOnProperty(name = "tesoreria.retiro-poller.enabled", havingValue = "true", matchIfMissing = false)
public class RetiroTesoreriaScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetiroTesoreriaScheduler.class);
    private static final int LOTE = 50;

    private final RetiroRepository retiroRepository;
    private final RetiroDetalleService retiroDetalleService;
    private final CajaVirtualService cajaVirtualService;
    private final TesoreriaService tesoreriaService;

    public RetiroTesoreriaScheduler(RetiroRepository retiroRepository, RetiroDetalleService retiroDetalleService,
                                    CajaVirtualService cajaVirtualService, TesoreriaService tesoreriaService) {
        this.retiroRepository = retiroRepository;
        this.retiroDetalleService = retiroDetalleService;
        this.cajaVirtualService = cajaVirtualService;
        this.tesoreriaService = tesoreriaService;
    }

    @Scheduled(
            fixedDelayString = "${tesoreria.retiro-poller.fixed-delay:60000}",
            initialDelayString = "${tesoreria.retiro-poller.initial-delay:30000}"
    )
    public void reconciliar() {
        try {
            List<Retiro> pendientes = retiroRepository.findPendientesIngresoCajaMayor(PageRequest.of(0, LOTE));
            int ok = 0;
            for (Retiro r : pendientes) {
                try {
                    if (procesar(r)) ok++;
                } catch (Exception e) {
                    log.warn("RetiroTesoreriaScheduler: retiro {}/{} no procesado: {}",
                            r.getId(), r.getSucursalId(), e.getMessage());
                }
            }
            if (ok > 0) log.info("RetiroTesoreriaScheduler: {} retiros ingresados a caja mayor", ok);
        } catch (Exception e) {
            log.warn("RetiroTesoreriaScheduler: error en reconciliación: {}", e.getMessage());
        }
    }

    @Transactional
    public boolean procesar(Retiro r) {
        // Guard anti doble-ingreso: relee y confirma que sigue pendiente dentro de la transacción.
        Retiro fresh = retiroRepository.findByIdAndSucursalId(r.getId(), r.getSucursalId());
        if (fresh == null || fresh.getCajaVirtualId() == null || fresh.getMovimientoCajaVirtualId() != null) {
            return false;
        }
        CajaVirtual caja = cajaVirtualService.findById(fresh.getCajaVirtualId()).orElse(null);
        if (caja == null) {
            log.warn("RetiroTesoreriaScheduler: caja mayor {} no existe (retiro {})", fresh.getCajaVirtualId(), fresh.getId());
            return false;
        }

        // Agrupar el efectivo del retiro por moneda.
        List<RetiroDetalle> detalles = retiroDetalleService.findByRetiroId(fresh.getId(), fresh.getSucursalId());
        Map<Long, Double> porMoneda = new HashMap<>();
        Map<Long, com.franco.dev.domain.financiero.Moneda> monedas = new HashMap<>();
        for (RetiroDetalle d : detalles) {
            if (d.getMoneda() == null || d.getCantidad() == null) continue;
            porMoneda.merge(d.getMoneda().getId(), d.getCantidad(), Double::sum);
            monedas.putIfAbsent(d.getMoneda().getId(), d.getMoneda());
        }

        Long ultimoMovId = null;
        for (Map.Entry<Long, Double> e : porMoneda.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
            mov.setCajaVirtual(caja);
            mov.setTipoMovimiento(CajaVirtualTipoMovimiento.INGRESO);
            mov.setCantidad(e.getValue());
            mov.setMoneda(monedas.get(e.getKey()));
            mov.setDescripcion("Ingreso por retiro de caja #" + fresh.getId() + "/" + fresh.getSucursalId());
            mov.setReferenciaId(fresh.getId());
            mov.setOrigenTipo(OrigenMovimientoTipo.RETIRO_CAJA);
            mov.setOrigenId(fresh.getId());
            MovimientoCajaVirtual posteado = tesoreriaService.registrar(mov);
            ultimoMovId = posteado.getId();
        }

        // Marca la fila como procesada (aunque el retiro no tuviera detalles, para no reintentar infinito).
        fresh.setMovimientoCajaVirtualId(ultimoMovId != null ? ultimoMovId : -1L);
        retiroRepository.save(fresh);
        return true;
    }
}
