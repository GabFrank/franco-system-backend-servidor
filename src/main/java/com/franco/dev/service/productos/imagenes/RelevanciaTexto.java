package com.franco.dev.service.productos.imagenes;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/**
 * Orden de los resultados por descripción según las palabras <b>distintivas</b> que nombran. Los buscadores
 * corrigen o ignoran las palabras raras de un producto local: con «DON FRANCO IPA HAPEE» Bing trae los
 * posts de Don Franco mezclados con IPAs «happy» de cualquier marca (probado el 2026-10-15). Una palabra
 * que está en casi todos los resultados («ipa», «brahma») no distingue nada; una que está en pocos
 * («franco», «hapee», «269») sí. Primero van las fotos que nombran más palabras distintivas en el título,
 * la página o la URL; dentro de cada grupo, el orden por tamaño ({@link SugerenciaImagenService#ordenarPorTamano})
 * y después el que traían.
 */
final class RelevanciaTexto {

    /** Una palabra es distintiva si la nombran como mucho esta fracción de los resultados. */
    private static final double FRACCION_DISTINTIVA = 0.5;
    /** Con menos resultados la frecuencia no dice nada. */
    private static final int MIN_RESULTADOS = 4;
    private static final Set<String> NO_CUENTAN = Set.of("unidad", "unidades", "unid", "und", "para", "con", "sin",
            "los", "las", "del", "promo", "pack", "caja");

    private RelevanciaTexto() {
    }

    static List<ImagenSugerida> ordenar(List<ImagenSugerida> imagenes, String texto, String tamano) {
        Set<String> palabras = palabras(texto);
        if (imagenes.size() < MIN_RESULTADOS || palabras.isEmpty()) {
            return SugerenciaImagenService.ordenarPorTamano(imagenes, tamano);
        }
        List<String> textos = new ArrayList<>(imagenes.size());
        for (ImagenSugerida i : imagenes) textos.add(textoDe(i));

        List<String> distintivas = new ArrayList<>();
        for (String p : palabras) {
            int nombran = 0;
            for (String t : textos) if (t.contains(p)) nombran++;
            if (nombran <= imagenes.size() * FRACCION_DISTINTIVA) distintivas.add(p);
        }
        if (distintivas.isEmpty()) return SugerenciaImagenService.ordenarPorTamano(imagenes, tamano);

        TreeMap<Integer, List<ImagenSugerida>> porPuntaje = new TreeMap<>((a, b) -> b - a);
        for (int k = 0; k < imagenes.size(); k++) {
            int puntaje = 0;
            for (String p : distintivas) if (textos.get(k).contains(p)) puntaje++;
            porPuntaje.computeIfAbsent(puntaje, x -> new ArrayList<>()).add(imagenes.get(k));
        }
        List<ImagenSugerida> res = new ArrayList<>(imagenes.size());
        for (List<ImagenSugerida> grupo : porPuntaje.values()) {
            res.addAll(SugerenciaImagenService.ordenarPorTamano(grupo, tamano));
        }
        return res;
    }

    /** Palabras de 3 o más letras o dígitos, en minúscula y sin acentos. */
    static Set<String> palabras(String texto) {
        Set<String> res = new LinkedHashSet<>();
        for (String p : normalizar(texto).split("[^a-z0-9]+")) {
            if (p.length() >= 3 && !NO_CUENTAN.contains(p)) res.add(p);
        }
        return res;
    }

    private static String textoDe(ImagenSugerida i) {
        return normalizar((i.getTitulo() == null ? "" : i.getTitulo()) + " " + (i.getPagina() == null ? "" : i.getPagina())
                + " " + i.getUrl());
    }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
