package com.franco.dev.service.financiero;

import com.franco.dev.graphql.financiero.publisher.TesoreriaCambio;
import com.franco.dev.graphql.financiero.publisher.TesoreriaCambioPublisher;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pasa el cambio de saldo a la suscripción recién cuando la transacción confirmó: un pago rechazado o un lote
 * revertido no hacen releer a nadie. {@code fallbackExecution}: sin transacción activa no hay nada que esperar.
 */
@Slf4j
@Component
@AllArgsConstructor
public class TesoreriaCambioListener {

    private final TesoreriaCambioPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCambio(TesoreriaCambio cambio) {
        // Una excepción acá le llega al que registró el pago aunque la plata ya entró, y podría reintentarlo.
        try {
            publisher.publish(cambio);
        } catch (Throwable t) {
            log.warn("No se pudo avisar el cambio de tesorería {}", cambio, t);
        }
    }
}
