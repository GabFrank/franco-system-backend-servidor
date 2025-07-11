package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class PedidoResolver implements GraphQLResolver<Pedido> {
    
    // All resolver methods removed since GraphQL schema now only includes entity fields
    // No computed fields = no resolvers needed
    
}
