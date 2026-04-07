package com.franco.dev.service.empresarial;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that syncs PostgreSQL publications with configuraciones.replication_table.
 * Runs at startup (after initial delay) and every hour so that new tables added via Flyway
 * (only CREATE TABLE + INSERT into replication_table) are automatically added to existing
 * publications on central and on all reachable filiales, without manual ALTER PUBLICATION.
 *
 * Usado en:
 * - Desktop: Sí (solo servidor central; las filiales son actualizadas por el central vía JDBC remoto).
 * - Mobile: No.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "replication.sync.enabled", havingValue = "true", matchIfMissing = false)
public class ReplicationPublicationSyncScheduler {

    private final LogicalReplicationService logicalReplicationService;

    public ReplicationPublicationSyncScheduler(LogicalReplicationService logicalReplicationService) {
        this.logicalReplicationService = logicalReplicationService;
    }

    @Scheduled(
        fixedDelayString = "${replication.sync.fixed-delay:3600000}",
        initialDelayString = "${replication.sync.initial-delay:120000}"
    )
    public void syncPublicationsWithReplicationTable() {
        log.info("ReplicationPublicationSyncScheduler: iniciando sincronización de publicaciones con replication_table");
        try {
            LogicalReplicationService.SetupFullReplicationResult result =
                logicalReplicationService.syncPublicationsWithReplicationTable();
            if (result.isSuccess()) {
                log.info("ReplicationPublicationSyncScheduler: sincronización completada. Log:\n{}", result.getMessage());
            } else {
                log.warn("ReplicationPublicationSyncScheduler: sincronización finalizada con errores. Log:\n{}", result.getMessage());
            }
        } catch (Exception e) {
            log.error("ReplicationPublicationSyncScheduler: error en sincronización", e);
        }
    }
}
