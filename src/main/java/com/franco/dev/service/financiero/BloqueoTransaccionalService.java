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
        // El cast: la función devuelve void y Hibernate no sabe mapear ese tipo (JDBC 1111).
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtextextended(:clave, 0)) as varchar)")
                .setParameter("clave", clave)
                .getSingleResult();
    }
}
