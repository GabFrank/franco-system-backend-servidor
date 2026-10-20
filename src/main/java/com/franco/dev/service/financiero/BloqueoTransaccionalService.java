package com.franco.dev.service.financiero;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

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
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void tomar(String clave, String esperaMaxima) {
        String previo = (String) entityManager.createNativeQuery("select current_setting('lock_timeout')")
                .getSingleResult();
        fijarLockTimeout(esperaMaxima);
        bloquear(clave);
        fijarLockTimeout(previo);
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
