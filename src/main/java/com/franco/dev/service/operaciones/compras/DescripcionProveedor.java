package com.franco.dev.service.operaciones.compras;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * La descripción del renglón tal como la escribe el proveedor, llevada a una clave estable: es lo que se aprende
 * cuando la factura (sobre todo la de papel) no trae el código del proveedor. Se busca primero igual a igual y, si
 * no hay, una {@link #parecida} (la IA no lee dos veces la misma foto igual).
 */
public final class DescripcionProveedor {

    static final int LARGO_MAXIMO = 500;

    private DescripcionProveedor() {
    }

    /** Mayúsculas, sin tildes, solo letras y números con un espacio entre palabras; vacío → {@code null}. */
    public static String normalizar(String descripcion) {
        if (descripcion == null) return null;
        String t = Normalizer.normalize(descripcion.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Z0-9]+", " ")
                .trim();
        if (t.length() > LARGO_MAXIMO) t = t.substring(0, LARGO_MAXIMO).trim();
        return t.isEmpty() ? null : t;
    }

    /** Desde estas palabras, lo que sigue es el vencimiento o el lote anotado en el papel, no el producto. */
    private static final Set<String> INICIO_DE_ANOTACION =
            new HashSet<>(Arrays.asList("VENC", "VENCE", "VENCIMIENTO", "VTO", "VTOS", "LOTE", "LOT"));

    /** Un peso o volumen: 800G, 100GR, 1L, 500ML, 269CC… */
    private static final Pattern MEDIDA = Pattern.compile("\\d+(G|GR|GRS|KG|ML|L|LT|LTS|CC|MG)");

    /**
     * Dos descripciones ya normalizadas que nombran lo mismo aunque la IA no las haya leído igual: se ignora el
     * vencimiento o lote anotado al final («… VENC 09 29») y el peso si uno de los dos no lo trae («PALMITO ENTERO
     * 12X1» = «PALMITO ENTERO 12X1 800G»). Todo lo demás tiene que coincidir: otro bulto («12X1» / «15X1»), otro
     * número o una palabra de más («COCA COLA» / «COCA COLA ZERO») ya no es la misma.
     */
    public static boolean parecida(String a, String b) {
        if (a == null || b == null) return false;
        List<String> palabrasA = sinAnotacion(a);
        List<String> palabrasB = sinAnotacion(b);
        List<String> nucleoA = palabrasA.stream().filter(p -> !MEDIDA.matcher(p).matches()).collect(Collectors.toList());
        List<String> nucleoB = palabrasB.stream().filter(p -> !MEDIDA.matcher(p).matches()).collect(Collectors.toList());
        if (nucleoA.isEmpty() || !nucleoA.equals(nucleoB)) return false;
        Set<String> medidasA = palabrasA.stream().filter(p -> MEDIDA.matcher(p).matches()).collect(Collectors.toSet());
        Set<String> medidasB = palabrasB.stream().filter(p -> MEDIDA.matcher(p).matches()).collect(Collectors.toSet());
        return medidasA.isEmpty() || medidasB.isEmpty() || medidasA.equals(medidasB);
    }

    private static List<String> sinAnotacion(String normalizada) {
        List<String> palabras = new ArrayList<>();
        for (String p : normalizada.split(" ")) {
            if (INICIO_DE_ANOTACION.contains(p)) break;
            if (!p.isEmpty()) palabras.add(p);
        }
        return palabras;
    }
}
