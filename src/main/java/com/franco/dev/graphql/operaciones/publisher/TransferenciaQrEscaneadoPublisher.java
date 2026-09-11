package com.franco.dev.graphql.operaciones.publisher;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

/**
 * Canal por el que viaja el aviso de escaneo hasta los desktops conectados.
 *
 * Sigue el mismo armado que {@code VentaCreditoQRAuthPublisher}: un
 * observable caliente, sin persistencia. Un desktop que se suscribe después
 * del escaneo no recibe el aviso, y está bien —para cuando eso pasa el
 * diálogo que había que cerrar ya no existe.
 */
@Slf4j
@Component
public class TransferenciaQrEscaneadoPublisher {

    private final Flowable<TransferenciaQrEscaneadoUpdate> publisher;

    private ObservableEmitter<TransferenciaQrEscaneadoUpdate> emitter;

    public TransferenciaQrEscaneadoPublisher() {
        Observable<TransferenciaQrEscaneadoUpdate> observable = Observable.create(emitter -> {
            this.emitter = emitter;
        });

        ConnectableObservable<TransferenciaQrEscaneadoUpdate> connectableObservable = observable.publish();
        connectableObservable.connect();

        publisher = connectableObservable.toFlowable(BackpressureStrategy.BUFFER);
    }

    public void publish(final TransferenciaQrEscaneadoUpdate entity) {
        ModelMapper m = new ModelMapper();
        TransferenciaQrEscaneadoUpdate entityUpdate = m.map(entity, TransferenciaQrEscaneadoUpdate.class);
        emitter.onNext(entityUpdate);
    }

    public Flowable<TransferenciaQrEscaneadoUpdate> getPublisher() {
        return publisher;
    }
}
