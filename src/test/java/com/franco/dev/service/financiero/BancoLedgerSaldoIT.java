package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT del ledger bancario contra la DB dev real (issue #376): un movimiento parte del saldo que hay en la base
 * aunque la cuenta ya estuviera cargada en la sesión con el saldo de antes. Es lo que pasa en una operación
 * financiera, cuyo resolver busca la cuenta antes de entrar a la transacción.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true. No es @Transactional: necesita un commit
 * de otra transacción en el medio. Deja dos entradas y dos salidas de 1 ("IT LEDGER BANCO"), saldo neto cero.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=BancoLedgerSaldoIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class BancoLedgerSaldoIT {

    private static final String MARCA = "IT LEDGER BANCO";

    @Autowired private BancoLedgerService bancoLedgerService;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private TransactionTemplate otraTx;
    private Long cuentaId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        otraTx = new TransactionTemplate(txManager);
        otraTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<?> cuentas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.cuenta_bancaria order by id").setMaxResults(1).getResultList());
        assumeTrue(cuentas != null && !cuentas.isEmpty(), "la base no tiene ninguna cuenta bancaria");
        cuentaId = ((Number) cuentas.get(0)).longValue();
    }

    private BigDecimal saldo() {
        return otraTx.execute(s -> (BigDecimal) em.createNativeQuery(
                "select saldo from financiero.cuenta_bancaria where id = :id").setParameter("id", cuentaId).getSingleResult());
    }

    private void entrada() {
        bancoLedgerService.registrar(cuentaId, MovimientoBancarioTipo.ENTRADA_MANUAL, BigDecimal.ONE, MARCA, "MANUAL", null, null);
    }

    @Test
    void unMovimientoNoPisaAlQueEntroMientrasLaCuentaYaEstabaCargada() {
        BigDecimal inicial = saldo();
        try {
            tx.execute(s -> {
                // La cuenta queda en la sesión con el saldo de ahora, como la deja el resolver.
                em.find(CuentaBancaria.class, cuentaId);
                // Otra transacción deposita 1 y commitea.
                otraTx.execute(o -> {
                    entrada();
                    return null;
                });
                // Este depósito tiene que sumar sobre ese, no sobre el saldo que tenía la instancia.
                entrada();
                return null;
            });
            assertEquals(0, inicial.add(new BigDecimal("2")).compareTo(saldo()), "un depósito pisó al otro");
        } finally {
            BigDecimal sobra = saldo().subtract(inicial);
            for (int i = 0; i < sobra.intValue(); i++) {
                bancoLedgerService.registrar(cuentaId, MovimientoBancarioTipo.SALIDA_MANUAL, BigDecimal.ONE, MARCA, "MANUAL", null, null);
            }
        }
    }
}
