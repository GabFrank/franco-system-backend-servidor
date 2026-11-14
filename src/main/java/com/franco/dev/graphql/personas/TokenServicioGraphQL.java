package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.TokenServicio;
import com.franco.dev.service.personas.TokenServicioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tokens de servicio para bots (issue #579). Todo es solo para superusuario: el chequeo es la primera
 * linea de cada metodo de {@link TokenServicioService}.
 */
@Component
public class TokenServicioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final TokenServicioService service;

    public TokenServicioGraphQL(TokenServicioService service) {
        this.service = service;
    }

    public List<TokenServicio> tokensServicio(Long usuarioId) {
        return service.listar(usuarioId);
    }

    public TokenServicioService.TokenServicioCreado crearTokenServicio(Long usuarioId, String nombre, Integer diasVigencia) {
        return service.crear(usuarioId, nombre, diasVigencia);
    }

    public Boolean revocarTokenServicio(Long id) {
        return service.revocar(id);
    }
}
