package com.franco.dev.graphql.configuracion;

import com.franco.dev.service.empresarial.ConfiguracionSistemaService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Version minima del desktop (V258.5). El desktop la consulta al iniciar sesion y, si es mas
 * viejo, no deja operar hasta actualizarse. Vacia = sin control. Se cambia en caliente, sin
 * reinicio, desde {@code empresarial.configuracion_sistema}.
 */
@Component
@AllArgsConstructor
public class VersionClienteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    public static final String CLAVE_VERSION_MINIMA_DESKTOP = "cliente.desktop.version_minima";

    private final ConfiguracionSistemaService configuracionSistema;

    /** No pide rol: la lee cualquier desktop al iniciar sesion. Null = sin control. */
    public String versionMinimaDesktop() {
        try {
            return configuracionSistema.valor(CLAVE_VERSION_MINIMA_DESKTOP).map(String::trim)
                    .filter(v -> !v.isEmpty()).orElse(null);
        } catch (RuntimeException e) {
            // Un control que no se puede leer no bloquea a nadie.
            return null;
        }
    }

    /** null o vacio apaga el control. Solo ADMIN: puede dejar sin operar a toda la empresa. */
    public String setVersionMinimaDesktop(String version) {
        configuracionSistema.exigirAdmin();
        String v = version != null ? version.trim() : "";
        if (!v.isEmpty() && !v.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new GraphQLException("La version tiene que tener la forma 4.12.0.");
        }
        configuracionSistema.guardar(CLAVE_VERSION_MINIMA_DESKTOP, v);
        return versionMinimaDesktop();
    }
}
