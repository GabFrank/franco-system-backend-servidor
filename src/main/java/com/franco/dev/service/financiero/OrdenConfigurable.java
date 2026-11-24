package com.franco.dev.service.financiero;

import graphql.GraphQLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Órdenes que se configuran en la caja mayor (cuentas bancarias, monedas, sucursales, operaciones
 * financieras). Se guardan como JSON array en {@code caja_virtual_configuracion}: ids ({@code [3,1,2]})
 * o nombres de enum ({@code ["DEPOSITO_BANCARIO","CAMBIO_DIVISA"]}).
 *
 * <p>Regla común: primero los que figuran en el orden, en ese orden; después el resto con el
 * comparador de respaldo, para que el resultado sea estable aunque no haya configuración.
 */
public final class OrdenConfigurable {

    private OrdenConfigurable() { }

    /** JSON array simple de ids a lista. Tolerante a null, vacío y basura (la ignora). */
    public static List<Long> parseIds(String json) {
        List<Long> out = new ArrayList<>();
        for (String part : partes(json)) {
            try { out.add(Long.parseLong(part)); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    /** JSON array simple de textos a lista (sin comillas). Tolerante a null y vacío. */
    public static List<String> parseNombres(String json) {
        List<String> out = new ArrayList<>();
        for (String part : partes(json)) {
            String v = part.replace("\"", "").trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    /** Ordena {@code items} según {@code ordenIds}; los que no figuran van al final, por {@code respaldo}. */
    public static <T> List<T> ordenar(Collection<T> items, Function<T, Long> id, String ordenIds,
                                      Comparator<T> respaldo) {
        List<T> lista = new ArrayList<>(items);
        List<Long> orden = parseIds(ordenIds);
        lista.sort((a, b) -> {
            int ia = orden.indexOf(id.apply(a));
            int ib = orden.indexOf(id.apply(b));
            if (ia < 0 && ib < 0) return respaldo.compare(a, b);
            if (ia < 0) return 1;
            if (ib < 0) return -1;
            return Integer.compare(ia, ib);
        });
        return lista;
    }

    /**
     * Valida un orden de ids antes de guardarlo: JSON array de enteros, sin repetidos. Devuelve el JSON
     * normalizado ({@code [3,1,2]}). {@code null} pasa tal cual (semántica PATCH: no se toca lo guardado).
     */
    public static String validarIds(String json, String campo) {
        if (json == null) return null;
        List<String> partes = partes(json);
        List<Long> ids = new ArrayList<>();
        for (String p : partes) {
            try { ids.add(Long.parseLong(p)); } catch (NumberFormatException e) {
                throw new GraphQLException(campo + ": «" + p + "» no es un id válido");
            }
        }
        if (new HashSet<>(ids).size() != ids.size()) throw new GraphQLException(campo + ": hay ids repetidos");
        return ids.toString().replace(" ", "");
    }

    /**
     * Valida una lista de nombres de un enum: solo valores existentes y sin repetir. Devuelve el JSON
     * normalizado ({@code ["A","B"]}). {@code null} pasa tal cual.
     */
    public static <E extends Enum<E>> String validarEnum(String json, Class<E> tipo, String campo) {
        if (json == null) return null;
        List<String> nombres = parseNombres(json);
        Set<String> validos = new HashSet<>();
        for (E e : EnumSet.allOf(tipo)) validos.add(e.name());
        Set<String> vistos = new HashSet<>();
        for (String n : nombres) {
            if (!validos.contains(n)) throw new GraphQLException(campo + ": «" + n + "» no es una operación válida");
            if (!vistos.add(n)) throw new GraphQLException(campo + ": «" + n + "» está repetida");
        }
        List<String> conComillas = new ArrayList<>();
        for (String n : nombres) conComillas.add("\"" + n + "\"");
        return "[" + String.join(",", conComillas) + "]";
    }

    private static List<String> partes(String json) {
        List<String> out = new ArrayList<>();
        if (json == null) return out;
        String s = json.replaceAll("[\\[\\]\\s]", "");
        if (s.isEmpty()) return out;
        for (String part : s.split(",")) {
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }
}
