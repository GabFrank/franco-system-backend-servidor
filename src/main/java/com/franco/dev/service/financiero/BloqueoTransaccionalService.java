package com.franco.dev.service.financiero;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.Set;

/**
 * Lock por nombre, hasta el fin de la transacción ({@code pg_advisory_xact_lock}).
 *
 * <p>Sirve para serializar dos pedidos sobre «lo mismo» cuando no hay una fila propia que tomar —un
 * número de comprobante que todavía no existe— o cuando tomarla hace daño: la fila de un maletín llega
 * por replicación desde la filial, y un {@code FOR UPDATE} frenaría al apply worker de esa filial.</p>
 *
 * <p>La clave se reduce a un bigint con {@code hashtextextended}: dos claves distintas que coincidan
 * solo se esperan una a la otra, no fallan. Tomarlo <b>antes</b> de cualquier {@code save}: la consulta
 * nativa vuelca lo pendiente de la sesión.</p>
 */
@Service
public class BloqueoTransaccionalService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void tomar(String clave) {
        bloquear(clave);
    }

    /**
     * Igual que {@link #tomar(String)}, pero la espera se corta a los {@code esperaMaxima} (formato de
     * {@code lock_timeout}, por ejemplo {@code "15s"}) con el error {@code 55P03} de PostgreSQL, que
     * aborta la transacción.
     *
     * <p>El {@code lock_timeout} vale solo para esta espera: después se restaura el valor que tenía la
     * transacción, así que las esperas de fila que vengan después quedan como estaban.</p>
     *
     * <p>Una clave ya tomada en esta transacción no se vuelve a pedir: el lock dura hasta el commit, y
     * cada consulta nativa vuelca la sesión entera. Pedirlo por cada ítem duplicaba el tiempo de avanzar
     * una transferencia de 118 ítems con lote (4,8 s → 10,3 s, #463).</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void tomar(String clave, String esperaMaxima) {
        Set<String> tomadas = tomadasEnEstaTransaccion();
        if (tomadas != null && tomadas.contains(clave)) {
            return;
        }
        String previo = (String) entityManager.createNativeQuery("select current_setting('lock_timeout')")
                .getSingleResult();
        fijarLockTimeout(esperaMaxima);
        bloquear(clave);
        fijarLockTimeout(previo);
        if (tomadas != null) {
            tomadas.add(clave);
        }
    }

    /**
     * Las claves que esta transacción ya tiene, colgadas de una sincronización de la propia transacción:
     * Spring la descarta al terminar, y un {@code REQUIRES_NEW} (otra conexión, que no tiene el lock)
     * arranca con la suya vacía. Null si no hay sincronización activa: entonces se pide siempre.
     */
    private static Set<String> tomadasEnEstaTransaccion() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return null;
        }
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            if (s instanceof ClavesTomadas) {
                return ((ClavesTomadas) s).claves;
            }
        }
        ClavesTomadas nuevas = new ClavesTomadas();
        TransactionSynchronizationManager.registerSynchronization(nuevas);
        return nuevas.claves;
    }

    private static final class ClavesTomadas implements TransactionSynchronization {
        private final Set<String> claves = new HashSet<>();
    }

    private void bloquear(String clave) {
        // El cast: la función devuelve void y Hibernate no sabe mapear ese tipo (JDBC 1111).
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtextextended(:clave, 0)) as varchar)")
                .setParameter("clave", clave)
                .getSingleResult();
    }

    private void fijarLockTimeout(String valor) {
        // set_config(..., true) es el SET LOCAL de una función: dura hasta el fin de la transacción.
        entityManager.createNativeQuery("select set_config('lock_timeout', :valor, true)")
                .setParameter("valor", valor)
                .getSingleResult();
    }
}
