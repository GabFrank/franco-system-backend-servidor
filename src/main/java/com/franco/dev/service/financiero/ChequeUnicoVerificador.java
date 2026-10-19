package com.franco.dev.service.financiero;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * Avisa al arrancar si falta el índice único de cheques (issue #376).
 *
 * <p>La migración V241.1 lo crea solo si no hay números repetidos, para no tumbar el arranque, y Flyway no
 * la reintenta: sin este aviso, un entorno podría quedar sin el respaldo y nadie se enteraría. No frena
 * el arranque ni falla si no puede consultar.</p>
 */
@Component
public class ChequeUnicoVerificador {

    private static final Logger log = LoggerFactory.getLogger(ChequeUnicoVerificador.class);
    static final String INDICE = "uq_cheque_chequera_numero";

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    public void verificar() {
        try {
            Number hay = (Number) entityManager.createNativeQuery(
                    "select count(*) from pg_indexes where schemaname = 'financiero' and indexname = :indice")
                    .setParameter("indice", INDICE).getSingleResult();
            if (hay == null || hay.intValue() == 0) {
                log.error("FALTA el indice unico {} en financiero.cheque: hay (o hubo) cheques con el mismo numero en una "
                        + "chequera. Corregirlos y crearlo: CREATE UNIQUE INDEX {} ON financiero.cheque (chequera_id, numero);",
                        INDICE, INDICE);
            }
        } catch (Exception e) {
            log.warn("No se pudo comprobar el indice {}: {}", INDICE, e.getMessage());
        }
    }
}
