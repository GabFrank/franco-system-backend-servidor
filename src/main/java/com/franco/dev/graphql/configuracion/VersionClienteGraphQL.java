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

    /**
     * X.Y.Z o con prerelease (4.2.0-alpha.345): el desktop compara con semver, y una alpha es menor que
     * su release; sin prerelease, en alpha la unica minima posible bloqueaba a todas (desktop#710).
     * En minusculas, como las versiones de Electron: semver distingue mayusculas y una minima
     * 4.2.0-ALPHA.345 quedaria por debajo de toda alpha, sin bloquear a nadie.
     */
    static final String PATRON_VERSION = "\\d+\\.\\d+\\.\\d+(-[0-9a-z-]+(\\.[0-9a-z-]+)*)?";

    /** null o vacio apaga el control. Solo ADMIN: puede dejar sin operar a toda la empresa. */
    public String setVersionMinimaDesktop(String version) {
        configuracionSistema.exigirAdmin();
        String v = version != null ? version.trim().toLowerCase(java.util.Locale.ROOT) : "";
        if (!v.isEmpty() && (v.length() > 64 || !v.matches(PATRON_VERSION))) {
            throw new GraphQLException("La version tiene que tener la forma 4.12.0 o 4.12.0-alpha.345.");
        }
        configuracionSistema.guardar(CLAVE_VERSION_MINIMA_DESKTOP, v);
        return versionMinimaDesktop();
    }
}
