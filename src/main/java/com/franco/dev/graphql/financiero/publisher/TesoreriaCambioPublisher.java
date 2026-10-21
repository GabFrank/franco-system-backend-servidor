package com.franco.dev.graphql.financiero.publisher;

import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Canal de la suscripción {@code tesoreriaCambioSub}. Mismo armado que {@code TransferenciaQrEscaneadoPublisher}
 * (observable caliente, sin persistencia), con tres diferencias:
 * <ul>
 *   <li>el emisor está serializado: publican varios hilos a la vez (requests y el poller de retiros);</li>
 *   <li>se entrega en otro hilo, para que el envío por el websocket no corra en el hilo que confirmó el pago;</li>
 *   <li>cada suscriptor tiene un buffer acotado: un desktop con el socket a medio cerrar no acumula sin límite.
 *       Perderle avisos viejos no importa: al reconectar relee la caja entera.</li>
 * </ul>
 */
@Slf4j
@Component
public class TesoreriaCambioPublisher {

    static final int BUFFER_POR_SUSCRIPTOR = 1000;

    private final Flowable<TesoreriaCambio> publisher;

    private ObservableEmitter<TesoreriaCambio> emitter;

    public TesoreriaCambioPublisher() {
        Observable<TesoreriaCambio> observable = Observable.create(e -> this.emitter = e.serialize());
        ConnectableObservable<TesoreriaCambio> connectable = observable.publish();
        connectable.connect();
        publisher = connectable.toFlowable(BackpressureStrategy.MISSING)
                .onBackpressureBuffer(BUFFER_POR_SUSCRIPTOR,
                        () -> log.debug("Suscriptor de tesoreriaCambioSub lento: se descartan avisos viejos"),
                        BackpressureOverflowStrategy.DROP_OLDEST)
                .observeOn(Schedulers.io());
    }

    public void publish(TesoreriaCambio cambio) {
        emitter.onNext(cambio);
    }

    public Flowable<TesoreriaCambio> getPublisher() {
        return publisher;
    }
}
