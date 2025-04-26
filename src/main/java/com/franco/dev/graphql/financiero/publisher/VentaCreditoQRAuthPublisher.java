package com.franco.dev.graphql.financiero.publisher;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VentaCreditoQRAuthPublisher {

    private final Flowable<VentaCreditoQRAuthUpdate> publisher;

    private ObservableEmitter<VentaCreditoQRAuthUpdate> emitter;

    public VentaCreditoQRAuthPublisher() {
        Observable<VentaCreditoQRAuthUpdate> observable = Observable.create(emitter -> {
            this.emitter = emitter;
        });

        observable.share();
        ConnectableObservable<VentaCreditoQRAuthUpdate> connectableObservable = observable.publish();
        connectableObservable.connect();


        publisher = connectableObservable.toFlowable(BackpressureStrategy.BUFFER);
    }

    public void publish(final VentaCreditoQRAuthUpdate entity) {
        ModelMapper m = new ModelMapper();
        VentaCreditoQRAuthUpdate entityUpdate = m.map(entity, VentaCreditoQRAuthUpdate.class);
        emitter.onNext(entityUpdate);
    }


    public Flowable<VentaCreditoQRAuthUpdate> getPublisher() {
        return publisher;
    }


}
