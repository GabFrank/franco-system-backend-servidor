package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Producto;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Puntúa coincidencias texto/descripción para ordenar resultados del buscador.
 * Prioriza prefijos al inicio, luego prefijos por palabra, subcadenas y por último
 * coincidencias aproximadas.
 *
 * <p>El tier aproximado se gradúa por distancia de edición real en vez de usar un
 * puntaje plano: la pasada tolerante del buscador puede traer muchos candidatos
 * flojos, y sin graduar quedarían todos empatados y ordenados por id, escondiendo
 * el producto correcto entre ellos.
 */
public final class ProductoTextoRelevanceScorer {

    private static final int SCORE_INICIO_DESCRIPCION = 10_000;
    private static final int SCORE_PALABRA_PREFIJO = 8_000;
    private static final int SCORE_CONTIENE = 6_000;
    private static final int SCORE_APROXIMADO = 4_000;
    private static final int SCORE_LUCENE_DEBIL = 100;
    private static final int SCORE_BASE_MULTI_TOKEN = 5_000;
    private static final int SCORE_PARCIAL_MULTI_TOKEN = 3_000;
    private static final int BONUS_ORDEN_PALABRAS = 1_000;
    private static final int PUNTOS_PALABRA_PREFIJO = 1_000;
    private static final int PUNTOS_CONTIENE = 500;
    private static final int PUNTOS_APROXIMADO = 300;
    private static final int PENALIZACION_EDICION = 400;
    private static final int PENALIZACION_EDICION_TOKEN = 60;
    private static final int PENALIZACION_TOKEN_SIN_MATCH = 600;

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
        // Con menos de 3 caracteres una edicion cambia demasiado el token
        // ("co" -> "ca"/"lo"/"do") y el resultado es ruido puro.
        if (longitudToken < 3) {
            return 0;
        }
        if (longitudToken < 6) {
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
        int sinMatch = 0;
        for (String token : tokens) {
            int tokenScore = puntuarTokenIndividual(texto, token);
            if (tokenScore <= 0) {
                sinMatch++;
            } else {
                score += tokenScore;
            }
        }

        if (sinMatch > 0) {
            // Coincidencia parcial: siempre por debajo de cualquier match completo,
            // pero ordenada por cuantas palabras si pegaron y que tan bien.
            int parcial = SCORE_PARCIAL_MULTI_TOKEN
                    - sinMatch * PENALIZACION_TOKEN_SIN_MATCH
                    + (score - SCORE_BASE_MULTI_TOKEN) / 10;
            return Math.max(SCORE_LUCENE_DEBIL, parcial);
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
        int distancia = distanciaMinimaPorPalabra(texto, token);
        if (esDistanciaSignificativa(distancia, token)) {
            return Math.max(SCORE_LUCENE_DEBIL + 1, SCORE_APROXIMADO - distancia * PENALIZACION_EDICION);
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
        int distancia = distanciaMinimaPorPalabra(texto, token);
        if (esDistanciaSignificativa(distancia, token)) {
            return Math.max(1, PUNTOS_APROXIMADO - distancia * PENALIZACION_EDICION_TOKEN);
        }
        return 0;
    }

    /**
     * Descarta distancias que no aportan senal: si hacen falta tantas ediciones
     * como letras tiene lo tipeado, la coincidencia es casualidad.
     */
    private static boolean esDistanciaSignificativa(int distancia, String token) {
        return distancia >= 0 && distancia < token.length();
    }

    private static int distanciaMinimaPorPalabra(String texto, String token) {
        int mejor = -1;
        for (String palabra : texto.split("\\s+")) {
            if (palabra.isEmpty()) {
                continue;
            }
            int distancia = distanciaLevenshtein(palabra, token);
            if (mejor < 0 || distancia < mejor) {
                mejor = distancia;
            }
        }
        return mejor;
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
