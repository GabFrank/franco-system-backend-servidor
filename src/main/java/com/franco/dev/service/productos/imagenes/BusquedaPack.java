package com.franco.dev.service.productos.imagenes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Presentaciones de varias unidades (pack, caja, fardo…). Con la descripción completa («BRAHMA SUB ZERO
 * LATA 269 ML PACK X 12 UNIDAD») Bing prioriza la lata suelta; con una consulta corta, sin el tamaño
 * («BRAHMA SUB ZERO pack 12 latas»), trae los packs (probado contra Bing el 2026-10-15: con el tamaño
 * se colaban packs de otras marcas del mismo tamaño). De lo que vuelve sirven las fotos cuyo título
 * nombra la marca y la cantidad (x12, c/12, 12 unidades…); las de otro tamaño, al final.
 */
final class BusquedaPack {

    private static final Pattern PALABRA_PACK = Pattern.compile("\\b(PACK|CAJA|FARDO|DISPLAY|FUNDA)\\b");
    /** «PACK X 12», «X 12», «X12», «C/12», «CAJA 24». */
    private static final Pattern CANTIDAD_EN_TEXTO =
            Pattern.compile("(?:\\bX|\\bC/|\\bPACK|\\bCAJA|\\bFARDO|\\bDISPLAY|\\bFUNDA)\\s*(?:X\\s*)?(\\d{1,3})\\b");
    /** Palabras de la presentación que no describen el producto y ensucian la búsqueda. */
    private static final Set<String> RUIDO = Set.of("PACK", "CAJA", "FARDO", "DISPLAY", "FUNDA", "X", "UNIDAD",
            "UNIDADES", "UNID", "UNID.", "UN", "UN.", "UND", "UDS", "PROMO", "PROMOCION", "PROMOCIÓN", "UNITARIO");
    private static final Set<String> ENVASES = Set.of("LATA", "LATAS", "BOTELLA", "BOTELLAS", "BOT", "PET");
    private static final Pattern UNIDAD_MEDIDA = Pattern.compile("(?i)ML|CC|CL|L|LT|LTS|LITROS?|G|GR|GRS|KG");
    private static final Pattern TAMANO =
            Pattern.compile("(?i)(?<![\\d.,])(\\d+(?:[.,]\\d+)?)\\s*(ml|cc|cl|l|lt|lts|litros?|g|gr|grs|kg)(?![a-z])");

    final int cantidad;
    /** En minúscula, como va en la consulta: «pack», «caja»… */
    final String palabra;

    private BusquedaPack(int cantidad, String palabra) {
        this.cantidad = cantidad;
        this.palabra = palabra;
    }

    /**
     * @param cantidadPresentacion la de la presentación; se usa solo con el texto armado por el central,
     *                             porque si el usuario escribió otro texto manda lo que escribió.
     * @return null si no es de varias unidades.
     */
    static BusquedaPack detectar(String texto, Double cantidadPresentacion, boolean textoDelUsuario) {
        String t = texto == null ? "" : texto.toUpperCase(Locale.ROOT);
        Integer n = null;
        if (!textoDelUsuario && cantidadPresentacion != null && cantidadPresentacion > 1
                && cantidadPresentacion == Math.floor(cantidadPresentacion) && cantidadPresentacion < 1000) {
            n = cantidadPresentacion.intValue();
        } else {
            Matcher m = CANTIDAD_EN_TEXTO.matcher(t);
            while (m.find() && n == null) {
                int v = Integer.parseInt(m.group(1));
                if (v > 1) n = v;
            }
        }
        if (n == null) return null;
        Matcher p = PALABRA_PACK.matcher(t);
        return new BusquedaPack(n, p.find() ? p.group(1).toLowerCase(Locale.ROOT) : "pack");
    }

    /**
     * Marca y variedad más «pack 12 latas»: sin el tamaño ni el envase, que van al orden y no a la
     * consulta. Lo que va desde «PARA» se descarta (es para quién es la promo).
     */
    String consulta(String texto) {
        String envase = null;
        for (String w : (texto == null ? "" : texto).toUpperCase(Locale.ROOT).split("\\s+")) {
            if (w.startsWith("LATA")) envase = "latas";
            else if (w.startsWith("BOT") || w.equals("PET")) envase = "botellas";
            if (envase != null) break;
        }
        List<String> quedan = palabrasDelProducto(texto);
        quedan.add(palabra + " " + cantidad + (envase != null ? " " + envase : ""));
        return String.join(" ", quedan);
    }

    /**
     * El título o la página nombran alguna de las dos primeras palabras del producto (casi siempre
     * la marca, o «CERVEZA BRAHMA»): sin esto se cuelan packs de otras marcas.
     */
    static boolean nombraMarca(ImagenSugerida i, String texto) {
        List<String> palabras = palabrasDelProducto(texto);
        String donde = sinAcentos(((i.getTitulo() == null ? "" : i.getTitulo()) + " "
                + (i.getPagina() == null ? "" : i.getPagina())).toLowerCase(Locale.ROOT));
        int revisadas = 0;
        for (String w : palabras) {
            if (w.length() < 4 || !w.matches("\\p{L}+")) continue;
            if (donde.contains(sinAcentos(w.toLowerCase(Locale.ROOT)))) return true;
            if (++revisadas == 2) break;
        }
        return revisadas == 0;
    }

    private static String sinAcentos(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Las palabras que nombran el producto: sin cantidades, precios, tamaños, envase ni lo de la promo. */
    static List<String> palabrasDelProducto(String texto) {
        String[] palabras = (texto == null ? "" : texto.trim()).split("\\s+");
        List<String> quedan = new ArrayList<>();
        for (int i = 0; i < palabras.length; i++) {
            String w = palabras[i];
            String up = w.toUpperCase(Locale.ROOT);
            if (up.isEmpty()) continue;
            if (up.equals("PARA")) break;
            if (RUIDO.contains(up) || ENVASES.contains(up) || up.startsWith("C/") || up.matches(".*\\d.*")) continue;
            // La unidad de un tamaño («269 ML»): el número ya se descartó.
            if (UNIDAD_MEDIDA.matcher(up).matches() && i > 0 && palabras[i - 1].matches("\\d+(?:[.,]\\d+)?")) continue;
            quedan.add(w);
        }
        return quedan;
    }

    /** El título o la página nombran la cantidad: «x12», «c/ 12», «pack 12», «12 unidades», «12x350ml», «com 12». */
    boolean nombraCantidad(ImagenSugerida i) {
        String n = String.valueOf(cantidad);
        Pattern antes = Pattern.compile("(?<![a-z])(?:x|c/|com|pack|kit|caja|cx|fardo|display|funda|de)\\s*\\.?\\s*"
                + n + "(?!\\d)(?!\\s*(?:ml|cc|cl|g|gr|kg|l|lt)(?![a-z]))");
        Pattern despues = Pattern.compile("(?<![\\d.,])" + n
                + "\\s*(?:x|un|und|unid|unidades|unidad|u|uds|latas?|botellas?|piezas|-?pack|packs)(?![a-z])");
        for (String s : new String[]{i.getTitulo(), i.getPagina()}) {
            if (s == null) continue;
            String l = s.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
            if (antes.matcher(l).find() || despues.matcher(l).find()) return true;
        }
        return false;
    }

    /**
     * Tamaño del producto llevado a ml o g («269 ML» → ml:269, «1 L» → ml:1000), o null si el texto no lo dice.
     */
    static String tamano(String texto) {
        List<String> l = tamanos(texto);
        return l.isEmpty() ? null : l.get(0);
    }

    /** El título nombra el tamaño del producto. */
    static boolean nombraTamano(ImagenSugerida i, String tamano) {
        return tamano != null && i.getTitulo() != null && tamanos(i.getTitulo()).contains(tamano);
    }

    /** El título nombra un tamaño de la misma medida y ninguno es el del producto: es otro producto. */
    static boolean nombraOtroTamano(ImagenSugerida i, String tamano) {
        if (tamano == null || i.getTitulo() == null) return false;
        String medida = tamano.substring(0, tamano.indexOf(':'));
        boolean otro = false;
        for (String t : tamanos(i.getTitulo())) {
            if (t.equals(tamano)) return false;
            if (t.startsWith(medida + ":")) otro = true;
        }
        return otro;
    }

    private static List<String> tamanos(String texto) {
        List<String> res = new ArrayList<>();
        if (texto == null) return res;
        Matcher m = TAMANO.matcher(texto);
        while (m.find()) {
            double v;
            try {
                v = Double.parseDouble(m.group(1).replace(',', '.'));
            } catch (NumberFormatException e) {
                continue;
            }
            String u = m.group(2).toLowerCase(Locale.ROOT);
            String medida;
            if (u.equals("ml") || u.equals("cc")) medida = "ml";
            else if (u.equals("cl")) { medida = "ml"; v *= 10; }
            else if (u.startsWith("l")) { medida = "ml"; v *= 1000; }
            else if (u.equals("kg")) { medida = "g"; v *= 1000; }
            else medida = "g";
            res.add(medida + ":" + Math.round(v));
        }
        return res;
    }
}
