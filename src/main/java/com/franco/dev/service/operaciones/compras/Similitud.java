package com.franco.dev.service.operaciones.compras;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Parecidos entre lo que dice la factura del proveedor y lo que tenemos cargado. */
final class Similitud {

    /** Palabras de la descripción del proveedor que no ayudan a buscar. */
    private static final Set<String> RUIDO = new LinkedHashSet<>(Arrays.asList(
            "DE", "DEL", "LA", "EL", "LOS", "LAS", "CON", "SIN", "Y", "X", "C", "UND", "UN", "UNID", "UNIDADES",
            "CAJA", "CAJ", "PAQ", "PQT", "PACK", "GR", "GRS", "G", "KG", "ML", "LT", "L", "CC"));

    private Similitud() {
    }

    /** Levenshtein: cuántos dígitos hay que cambiar, sacar o poner para pasar de un código al otro. */
    static int distancia(String a, String b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + costo);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /**
     * Textos para el buscador de productos a partir de la descripción del proveedor, del más preciso al más
     * amplio. «SENSEI PAPAS FRITAS PIZZA 140gr.C/20» → «SENSEI PAPAS FRITAS PIZZA», «SENSEI PAPAS».
     */
    static List<String> consultasDescripcion(String descripcion) {
        List<String> consultas = new ArrayList<>();
        if (descripcion == null) return consultas;
        String limpio = descripcion.toUpperCase(Locale.ROOT)
                .replaceAll("C/\\s*\\d+", " ")
                .replaceAll("X\\s*\\d+", " ")
                .replaceAll("[^A-ZÁÉÍÓÚÑÜ0-9 ]", " ");
        List<String> palabras = Arrays.stream(limpio.split("\\s+"))
                .filter(p -> p.length() > 1 && !RUIDO.contains(p) && !p.matches("\\d+[A-Z]*"))
                .collect(Collectors.toList());
        if (palabras.isEmpty()) return consultas;
        consultas.add(String.join(" ", palabras.subList(0, Math.min(4, palabras.size()))));
        if (palabras.size() > 2) consultas.add(String.join(" ", palabras.subList(0, 2)));
        return consultas.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Unidades por bulto que dice la descripción del proveedor: «SALADIX JAMON 6*100GR» → 6,
     * «BONOBON ORIGINAL 30X15G (12)» → 30, «COFLER BLOCK 20*38GR» → 20, «GOLPE 24UX25G» → 24. Null si no lo dice.
     */
    static Integer unidadesPorBulto(String descripcion) {
        if (descripcion == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?<![\\d.,])(\\d{1,3})\\s*(?:UND|UN|U)?\\s*[*xX]\\s*\\d").matcher(descripcion);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    /**
     * Los números de bulto de la descripción: los que van antes de una «x» o un «*» («30X15G», «12*24*18G»), los
     * de «24UN», y los entre paréntesis («(12)», «FILI(10)»). No los gramajes: «15G», «810gr».
     */
    static java.util.List<Integer> numerosDeBulto(String descripcion) {
        java.util.List<Integer> numeros = new java.util.ArrayList<>();
        if (descripcion == null) return numeros;
        String d = descripcion.toUpperCase(java.util.Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?<![\\d.,])(\\d{1,4})\\s*(?:UND|UN|U)?\\s*[*X]\\s*\\d").matcher(d);
        int desde = 0;
        while (m.find(desde)) {
            numeros.add(Integer.valueOf(m.group(1)));
            desde = m.end(1);
        }
        m = java.util.regex.Pattern.compile("\\((\\d{1,4})\\)").matcher(d);
        while (m.find()) numeros.add(Integer.valueOf(m.group(1)));
        m = java.util.regex.Pattern.compile("(?<![\\d,])(\\d{1,4})\\s*UN(?:D|IDADES)?\\b").matcher(d);
        while (m.find()) numeros.add(Integer.valueOf(m.group(1)));
        return numeros;
    }
}
