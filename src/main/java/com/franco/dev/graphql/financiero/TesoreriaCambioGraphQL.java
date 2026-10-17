package com.franco.dev.graphql.financiero;

import com.franco.dev.graphql.financiero.publisher.TesoreriaCambio;
import com.franco.dev.graphql.financiero.publisher.TesoreriaCambioPublisher;
import graphql.kickstart.tools.GraphQLSubscriptionResolver;
import lombok.AllArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;

/** Cambios de saldo de cajas y cuentas, para que el desktop relea la caja mayor abierta. */
@Component
@AllArgsConstructor
public class TesoreriaCambioGraphQL implements GraphQLSubscriptionResolver {

    private final TesoreriaCambioPublisher publisher;

    public Publisher<TesoreriaCambio> tesoreriaCambioSub() {
        return publisher.getPublisher();
    }
}
