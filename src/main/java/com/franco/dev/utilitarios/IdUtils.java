package com.franco.dev.utilitarios;

import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utilidades para normalizar los ids que llegan desde GraphQL.
 *
 * Los campos declarados como [Int] en el schema llegan como Integer y los [ID]
 * como String, aunque el resolver los reciba tipados como List&lt;Long&gt;: el
 * borrado de tipos hace que nadie los convierta. Cualquier codigo que use esos
 * ids como Long revienta con ClassCastException, asi que hay que normalizarlos
 * antes de usarlos.
 */
public class IdUtils {

    private IdUtils() {
    }

    /**
     * Convierte una lista de ids de GraphQL (Integer, Long o String) en Long.
     * Devuelve null si la lista viene vacia o nula, para no romper los filtros
     * que interpretan null como "sin filtro".
     */
    @Nullable
    public static List<Long> toLongList(@Nullable List<?> idList) {
        if (idList == null || idList.isEmpty()) {
            return null;
        }
        List<Long> converted = idList.stream()
                .filter(Objects::nonNull)
                .map(IdUtils::toLong)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return converted.isEmpty() ? null : converted;
    }

    /**
     * Convierte un id de GraphQL en Long. Devuelve null si el valor no es numerico.
     */
    @Nullable
    public static Long toLong(@Nullable Object id) {
        if (id == null) {
            return null;
        }
        if (id instanceof Number) {
            return ((Number) id).longValue();
        }
        try {
            return Long.parseLong(id.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
