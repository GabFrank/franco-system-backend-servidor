package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.repository.financiero.RetiroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Puente Retiro (caja PDV) → Caja Mayor. El retiro nace en la filial y llega a
 * central por replicación lógica de PostgreSQL (BRANCH_TO_MAIN), sin pasar por
 * Spring — por eso la integración es un poller reconciliador, no un evento/hook
 * (mismo patrón que SIFEN/cotización). Cada retiro pendiente se procesa en su propia
 * transacción vía {@link RetiroTesoreriaProcesador} (bean separado para que
 * {@code @Transactional} aplique — no self-invocation), garantizando la atomicidad
 * del ingreso + marcado (idempotente + guard anti doble-ingreso).
 *
 * Desactivado por defecto ({@code matchIfMissing=false}), se habilita por instancia.
 */
@Component
@ConditionalOnProperty(name = "tesoreria.retiro-poller.enabled", havingValue = "true", matchIfMissing = false)
public class RetiroTesoreriaScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetiroTesoreriaScheduler.class);
    private static final int LOTE = 50;

    private final RetiroRepository retiroRepository;
    private final RetiroTesoreriaProcesador procesador;

    public RetiroTesoreriaScheduler(RetiroRepository retiroRepository, RetiroTesoreriaProcesador procesador) {
        this.retiroRepository = retiroRepository;
        this.procesador = procesador;
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
                    if (procesador.procesar(r.getId(), r.getSucursalId(), null)) ok++;
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
}
