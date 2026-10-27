package com.franco.dev.service.operaciones;

import com.franco.dev.service.empresarial.LogicalReplicationService;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Consultas de solo lectura a la base de una filial, para operaciones del central que escriben
 * filas que van a bajar a esa filial (lotes fase 3).
 *
 * Todas devuelven null cuando la filial no responde: quien llama decide si eso bloquea.
 */
@Log
@Service
public class SincronizacionFilialService {

    @Autowired
    private LogicalReplicationService logicalReplicationService;

    /**
     * Cuántos de estos lotes ya tiene la filial. Una fila del ledger que baja con un lote_id que la
     * filial no tiene entra igual (los apply workers no disparan FK) y queda huérfana.
     */
    public Long lotesPresentesEnFilial(Long sucursalId, Collection<Long> loteIds) {
        if (loteIds == null || loteIds.isEmpty()) {
            return 0L;
        }
        StringBuilder in = new StringBuilder();
        for (Long id : loteIds) {
            if (id == null) {
                continue;
            }
            if (in.length() > 0) {
                in.append(',');
            }
            in.append(id.longValue());
        }
        try {
            return logicalReplicationService.queryRemoteForObject(sucursalId,
                    "SELECT count(*) FROM operaciones.lote WHERE id IN (" + in + ")", Long.class);
        } catch (Exception e) {
            log.warning("No se pudo consultar los lotes de la filial " + sucursalId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Segundos desde la última señal de replicación en los dos sentidos, medido en la filial: lo
     * que recibe del central (pg_stat_subscription) y lo que el central le confirma
     * (pg_stat_replication.reply_time). Los dos se actualizan con los keepalive aunque no haya
     * ventas, así que una sucursal quieta no parece atrasada. Toma el peor de los dos; una suscripción sin worker o sin lector del central cuenta como 1e9.
     */
    public Double atrasoSegundos(Long sucursalId) {
        try {
            return logicalReplicationService.queryRemoteForObject(sucursalId,
                    "SELECT GREATEST(" +
                    " (SELECT EXTRACT(EPOCH FROM now() - MIN(last_msg_receipt_time)) FROM pg_stat_subscription WHERE relid IS NULL)," +
                    " (SELECT EXTRACT(EPOCH FROM now() - MIN(reply_time)) FROM pg_stat_replication)," +
                    // Una suscripcion sin worker (deshabilitada o caida) no deja fila con fecha y
                    // el MIN la ignoraria: cuenta como atraso infinito. Lo mismo si el central no
                    // esta leyendo la publicacion de subida de la filial.
                    " (SELECT 1e9 FROM pg_subscription s LEFT JOIN pg_stat_subscription st" +
                    "    ON st.subid = s.oid AND st.relid IS NULL WHERE st.pid IS NULL LIMIT 1)," +
                    " (SELECT 1e9 WHERE NOT EXISTS (SELECT 1 FROM pg_stat_replication)))::double precision",
                    Double.class);
        } catch (Exception e) {
            log.warning("No se pudo medir la sincronizacion de la filial " + sucursalId + ": " + e.getMessage());
            return null;
        }
    }
}
