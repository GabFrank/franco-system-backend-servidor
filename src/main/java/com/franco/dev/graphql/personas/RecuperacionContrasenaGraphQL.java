package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.RecuperacionContrasena;
import com.franco.dev.service.personas.RecuperacionContrasenaService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Lo que ve y hace RRHH con las solicitudes de recuperacion de contrasena de quienes no tienen
 * correo. El lado del usuario sin sesion esta en {@code RecuperacionContrasenaController}. Exige
 * el rol RRHH APROBAR (o ADMIN), chequeado en el servicio.
 */
@Component
public class RecuperacionContrasenaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final RecuperacionContrasenaService service;

    public RecuperacionContrasenaGraphQL(RecuperacionContrasenaService service) {
        this.service = service;
    }

    public Page<RecuperacionContrasena> solicitudesRecuperacionContrasena(String estado, int page, int size) {
        return service.listarSolicitudesRrhh(estado, page, size);
    }

    public Integer countSolicitudesRecuperacionContrasenaPendientes() {
        return (int) service.contarPendientesRrhh();
    }

    public RecuperacionContrasena aprobarRecuperacionContrasena(Long id) {
        return service.aprobar(id);
    }

    public RecuperacionContrasena rechazarRecuperacionContrasena(Long id, String motivo) {
        return service.rechazar(id, motivo);
    }
}
