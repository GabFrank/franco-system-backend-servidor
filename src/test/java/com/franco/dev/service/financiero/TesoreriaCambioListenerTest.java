package com.franco.dev.service.financiero;

import com.franco.dev.graphql.financiero.publisher.TesoreriaCambio;
import com.franco.dev.graphql.financiero.publisher.TesoreriaCambioPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El aviso sale recién con el commit. Contexto Spring real (lo que procesa {@code @TransactionalEventListener}) y
 * transacción simulada con la sincronización de Spring: sin base de datos.
 */
class TesoreriaCambioListenerTest {

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        TesoreriaCambioPublisher publisher() {
            return mock(TesoreriaCambioPublisher.class);
        }

        @Bean
        TesoreriaCambioListener listener(TesoreriaCambioPublisher publisher) {
            return new TesoreriaCambioListener(publisher);
        }
    }

    private AnnotationConfigApplicationContext ctx;
    private TesoreriaCambioPublisher publisher;
    private final TesoreriaCambio cambio = TesoreriaCambio.deCaja(1L);

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(Config.class);
        publisher = ctx.getBean(TesoreriaCambioPublisher.class);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        ctx.close();
    }

    @Test
    void dentro_de_una_transaccion_avisa_recien_al_confirmar() {
        enTransaccion();
        ctx.publishEvent(cambio);
        verify(publisher, never()).publish(any());

        terminar(TransactionSynchronization.STATUS_COMMITTED);

        verify(publisher).publish(cambio);
    }

    @Test
    void si_la_transaccion_se_revierte_no_avisa() {
        enTransaccion();
        ctx.publishEvent(cambio);

        terminar(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(publisher, never()).publish(any());
    }

    @Test
    void sin_transaccion_avisa_en_el_momento() {
        ctx.publishEvent(cambio);
        verify(publisher).publish(cambio);
    }

    @Test
    void si_el_aviso_falla_no_le_llega_el_error_a_quien_movio_la_plata() {
        doThrow(new IllegalStateException("socket roto")).when(publisher).publish(any());
        assertDoesNotThrow(() -> ctx.publishEvent(cambio));
    }

    /** Lo que hace el transaction manager al abrir una transacción: Spring mira las dos cosas. */
    private void enTransaccion() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    /** Lo que hace el transaction manager al terminar: afterCommit (solo si confirmó) y afterCompletion. */
    private void terminar(int status) {
        java.util.List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            TransactionSynchronizationUtils.invokeAfterCommit(syncs);
        }
        TransactionSynchronizationUtils.invokeAfterCompletion(syncs, status);
    }
}
