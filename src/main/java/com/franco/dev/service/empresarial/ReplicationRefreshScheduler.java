package com.franco.dev.service.empresarial;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that runs REFRESH PUBLICATION on all subscriptions (local + every filial).
 * Lightweight: only syncs the list of tables from each publication, no data copy.
 *
 * Use case: when a filial was unreachable during the publication sync, it misses the refresh.
 * When the filial comes back, this scheduler (e.g. every 2 hours) will refresh it so it
 * picks up any new tables added to the publications.
 *
 * Can be enabled independently of replication.sync.enabled (e.g. sync every 1 h, refresh every 2 h).
 *
 * Usado en:
 * - Desktop: Sí (solo servidor central).
 * - Mobile: No.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "replication.refresh.enabled", havingValue = "true", matchIfMissing = false)
public class ReplicationRefreshScheduler {

    private final LogicalReplicationService logicalReplicationService;

    public ReplicationRefreshScheduler(LogicalReplicationService logicalReplicationService) {
        this.logicalReplicationService = logicalReplicationService;
    }

    @Scheduled(
        fixedDelayString = "${replication.refresh.fixed-delay:7200000}",
        initialDelayString = "${replication.refresh.initial-delay:300000}"
    )
    public void refreshAllSubscriptionsEverywhere() {
        log.info("ReplicationRefreshScheduler: refrescando todas las suscripciones (central + filiales)");
        try {
            logicalReplicationService.refreshAllSubscriptionsEverywhere();
            log.info("ReplicationRefreshScheduler: refresh completado");
        } catch (Exception e) {
            log.error("ReplicationRefreshScheduler: error en refresh", e);
        }
    }
}
