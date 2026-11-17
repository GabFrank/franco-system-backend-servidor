package com.franco.dev.graphql.administrativo.resolver;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.service.administrativo.HorarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Campos calculados de Horario. No se llama HorarioResolver: ese nombre ya es el bean de
 * service/administrativo/helper que elige el horario al marcar.
 */
@Component
public class HorarioGraphQLResolver implements GraphQLResolver<Horario> {

    @Autowired
    private HorarioService horarioService;

    public Integer cantidadAsignados(Horario horario) {
        return horarioService.cantidadAsignados(horario.getId());
    }
}
