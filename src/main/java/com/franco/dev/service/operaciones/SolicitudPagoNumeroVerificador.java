package com.franco.dev.service.operaciones;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Deja lista al arrancar la secuencia que numera las solicitudes de pago: la crea si falta y la adelanta si
 * quedó detrás del número más alto. Solo adelanta, nunca retrocede.
 *
 * <p>La migración V242.1 ya la crea y la alinea, pero Flyway no repite una versión aplicada. Esto cubre lo
 * que queda afuera: un entorno donde la migración no corrió, la vuelta a este JAR después de un rollback en
 * el que el anterior siguió numerando por conteo, y los segundos entre la migración y el reemplazo del JAR.
 * Sin la secuencia no entra ninguna solicitud; atrasada, las altas fallan hasta pasar el número más alto.</p>
 *
 * <p>No frena el arranque: si no puede, lo dice con ERROR y el alta fallará con el mensaje de la base.</p>
 */
@Component
@RequiredArgsConstructor
public class SolicitudPagoNumeroVerificador {

    private static final Logger log = LoggerFactory.getLogger(SolicitudPagoNumeroVerificador.class);

    static final String SECUENCIA = "operaciones.solicitud_pago_numero_seq";
    static final String CREAR = "CREATE SEQUENCE IF NOT EXISTS " + SECUENCIA;
    /** El número que daría el próximo {@code nextval}. */
    static final String PROXIMO = "select case when is_called then last_value + 1 else last_value end from " + SECUENCIA;
    /** El más alto con forma SP-<dígitos>, más uno. Mismo criterio que la migración. */
    static final String MINIMO = "select coalesce(max(case when numero_solicitud ~ '^SP-[0-9]{1,15}$' "
            + "then substring(numero_solicitud from 4)::bigint end), 0) + 1 from operaciones.solicitud_pago";
    static final String ADELANTAR = "select setval('" + SECUENCIA + "', ?, false)";

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void alinear() {
        try {
            jdbc.execute(CREAR);
            Long proximo = jdbc.queryForObject(PROXIMO, Long.class);
            Long minimo = jdbc.queryForObject(MINIMO, Long.class);
            if (proximo == null || minimo == null || minimo <= proximo) return;
            jdbc.queryForObject(ADELANTAR, Long.class, minimo);
            log.warn("La secuencia {} estaba atrasada (iba a dar {}, ya existe hasta {}): se adelanto a {}.",
                    SECUENCIA, proximo, minimo - 1, minimo);
        } catch (Exception e) {
            log.error("No se pudo dejar lista la secuencia {} (migracion V242.1): sin ella no se pueden crear "
                    + "solicitudes de pago. Motivo: {}", SECUENCIA, e.getMessage());
        }
    }
}
