package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Producto;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Puntúa coincidencias texto/descripción para ordenar resultados del buscador.
 * Prioriza prefijos al inicio, luego prefijos por palabra, subcadenas y por último fuzzy.
 */
public final class ProductoTextoRelevanceScorer {

    private static final int SCORE_INICIO_DESCRIPCION = 10_000;
    private static final int SCORE_PALABRA_PREFIJO = 8_000;
    private static final int SCORE_CONTIENE = 6_000;
    private static final int SCORE_FUZZY_PALABRA = 2_000;
    private static final int SCORE_LUCENE_DEBIL = 100;
    private static final int SCORE_BASE_MULTI_TOKEN = 5_000;
    private static final int BONUS_ORDEN_PALABRAS = 1_000;
    private static final int PUNTOS_PALABRA_PREFIJO = 1_000;
    private static final int PUNTOS_CONTIENE = 500;
    private static final int PUNTOS_FUZZY = 200;

    private ProductoTextoRelevanceScorer() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    public static int puntuar(Producto producto, String textoBusqueda) {
        if (producto == null || textoBusqueda == null) {
            return 0;
        }
        String consulta = normalizar(textoBusqueda);
        if (consulta.isEmpty()) {
            return 0;
        }

        int desc = puntuarTexto(normalizar(producto.getDescripcion()), consulta);
        int factura = puntuarTexto(normalizar(producto.getDescripcionFactura()), consulta);
        return Math.max(desc, factura);
    }

    static int distanciaFuzzyMaxima(int longitudToken) {
        if (longitudToken < 5) {
            return 0;
        }
        if (longitudToken <= 7) {
            return 1;
        }
        return 2;
    }

    private static int puntuarTexto(String texto, String consulta) {
        if (texto == null || texto.isEmpty()) {
            return 0;
        }

        String[] tokens = consulta.split("\\s+");
        if (tokens.length == 1) {
            return puntuarTokenUnico(texto, tokens[0]);
        }

        int score = SCORE_BASE_MULTI_TOKEN;
        for (String token : tokens) {
            int tokenScore = puntuarTokenIndividual(texto, token);
            if (tokenScore <= 0) {
                return SCORE_LUCENE_DEBIL;
            }
            score += tokenScore;
        }
        if (aparecenEnOrden(texto, tokens)) {
            score += BONUS_ORDEN_PALABRAS;
        }
        return score;
    }

    private static int puntuarTokenUnico(String texto, String token) {
        if (texto.startsWith(token)) {
            return SCORE_INICIO_DESCRIPCION - Math.min(texto.length(), 999);
        }
        int indicePalabra = indicePalabraQueEmpiezaCon(texto, token);
        if (indicePalabra >= 0) {
            return SCORE_PALABRA_PREFIJO - Math.min(indicePalabra, 999);
        }
        int indice = texto.indexOf(token);
        if (indice >= 0) {
            return SCORE_CONTIENE - Math.min(indice, 999);
        }
        if (coincidenciaFuzzyEnPalabra(texto, token)) {
            return SCORE_FUZZY_PALABRA;
        }
        return SCORE_LUCENE_DEBIL;
    }

    private static int puntuarTokenIndividual(String texto, String token) {
        if (palabraEmpiezaCon(texto, token)) {
            return PUNTOS_PALABRA_PREFIJO;
        }
        if (texto.contains(token)) {
            return PUNTOS_CONTIENE;
        }
        if (coincidenciaFuzzyEnPalabra(texto, token)) {
            return PUNTOS_FUZZY;
        }
        return 0;
    }

    private static boolean palabraEmpiezaCon(String texto, String prefijo) {
        return indicePalabraQueEmpiezaCon(texto, prefijo) >= 0;
    }

    private static int indicePalabraQueEmpiezaCon(String texto, String prefijo) {
        int posicion = 0;
        for (String palabra : texto.split("\\s+")) {
            if (palabra.startsWith(prefijo)) {
                return posicion;
            }
            posicion += palabra.length() + 1;
        }
        return -1;
    }

    private static boolean coincidenciaFuzzyEnPalabra(String texto, String token) {
        int maxEdits = distanciaFuzzyMaxima(token.length());
        if (maxEdits == 0) {
            return false;
        }
        for (String palabra : texto.split("\\s+")) {
            if (distanciaLevenshtein(palabra, token) <= maxEdits) {
                return true;
            }
        }
        return false;
    }

    private static boolean aparecenEnOrden(String texto, String[] tokens) {
        int desde = 0;
        for (String token : tokens) {
            int indice = texto.indexOf(token, desde);
            if (indice < 0) {
                return false;
            }
            desde = indice + token.length();
        }
        return true;
    }

    private static int distanciaLevenshtein(String a, String b) {
        int[][] matriz = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            matriz[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            matriz[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                matriz[i][j] = Math.min(
                        Math.min(matriz[i - 1][j] + 1, matriz[i][j - 1] + 1),
                        matriz[i - 1][j - 1] + costo);
            }
        }
        return matriz[a.length()][b.length()];
    }
}
