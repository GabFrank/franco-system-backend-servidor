package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lo que se puede preguntarle al {@code mapeo} de un formato: que campos produce y cuales son
 * obligatorios.
 *
 * <p><b>Con Jackson y no con un regex</b>, a diferencia de {@link FormatoTerminalPosService}, que
 * valida la forma del mapeo a mano. El motivo de aquella decision era no arrastrar una dependencia
 * de parseo al filial, que lee el mismo JSON; esto corre solo en el ABM de central, donde Jackson ya
 * esta. Y aca la precision importa: un regex sobre las claves tambien matchearia las <b>anidadas</b>
 * --el {@code mapa} de una regla-- y daria por bueno un campo que no existe.
 *
 * <p>Ante un mapeo ilegible devuelve vacio en vez de romper: la validacion de forma es
 * responsabilidad de {@code FormatoTerminalPosService}, y estos chequeos no pueden bloquear por un
 * motivo ajeno.
 */
final class MapeoFormato {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MapeoFormato() {
    }

    /** Las claves de primer nivel: los campos que el formato produce. */
    static Set<String> campos(String mapeo) {
        Set<String> out = new LinkedHashSet<String>();
        JsonNode root = leer(mapeo);
        if (root == null) return out;
        Iterator<String> it = root.fieldNames();
        while (it.hasNext()) out.add(it.next());
        return out;
    }

    /**
     * Los campos que el mapeo marca obligatorios.
     *
     * <p>Deciden tres cosas de una vez: que tiene que encontrar el OCR para que la operacion no
     * falle, cuando el resultado es utilizable, y que campos pide el formulario de carga a mano.
     */
    static Set<String> obligatorios(String mapeo) {
        Set<String> out = new LinkedHashSet<String>();
        JsonNode root = leer(mapeo);
        if (root == null) return out;
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode obligatorio = e.getValue() != null ? e.getValue().get("obligatorio") : null;
            if (obligatorio != null && obligatorio.asBoolean(false)) out.add(e.getKey());
        }
        return out;
    }

    private static JsonNode leer(String mapeo) {
        if (mapeo == null || mapeo.trim().isEmpty()) return null;
        try {
            JsonNode root = JSON.readTree(mapeo);
            return root != null && root.isObject() ? root : null;
        } catch (Exception e) {
            return null;
        }
    }
}
