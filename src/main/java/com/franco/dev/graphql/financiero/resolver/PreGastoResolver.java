package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.PreGasto;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class PreGastoResolver implements GraphQLResolver<PreGasto> {
    // Los campos lazy de PreGasto se resuelven automáticamente por Hibernate/JPA.
    // Este resolver se mantiene como punto de extensión para campos calculados futuros.
}
