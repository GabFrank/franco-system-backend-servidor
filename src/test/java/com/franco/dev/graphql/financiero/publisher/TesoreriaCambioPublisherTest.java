package com.franco.dev.graphql.financiero.publisher;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TesoreriaCambioPublisherTest {

    @Test
    void publicar_sin_suscriptores_no_falla() {
        TesoreriaCambioPublisher publisher = new TesoreriaCambioPublisher();
        assertDoesNotThrow(() -> publisher.publish(TesoreriaCambio.deCaja(1L)));
    }

    @Test
    void el_suscriptor_recibe_lo_publicado() throws InterruptedException {
        TesoreriaCambioPublisher publisher = new TesoreriaCambioPublisher();
        TestSubscriber<TesoreriaCambio> sub = publisher.getPublisher().test();

        publisher.publish(TesoreriaCambio.deCaja(1L));
        publisher.publish(TesoreriaCambio.deCuenta(4L));

        // Se entrega en otro hilo.
        sub.awaitCount(2);
        sub.assertValues(TesoreriaCambio.deCaja(1L), TesoreriaCambio.deCuenta(4L));
    }

    @Test
    void un_suscriptor_que_no_consume_no_frena_al_que_publica() {
        TesoreriaCambioPublisher publisher = new TesoreriaCambioPublisher();
        // Pide cero: los avisos se acumulan hasta el tope y después se descartan los más viejos.
        TestSubscriber<TesoreriaCambio> lento = publisher.getPublisher().test(0);

        assertDoesNotThrow(() -> {
            for (long i = 0; i < TesoreriaCambioPublisher.BUFFER_POR_SUSCRIPTOR * 3L; i++) {
                publisher.publish(TesoreriaCambio.deCaja(i));
            }
        });
        lento.assertNoErrors();
    }
}
