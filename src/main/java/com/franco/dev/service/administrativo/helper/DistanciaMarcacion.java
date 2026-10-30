package com.franco.dev.service.administrativo.helper;

import java.math.BigDecimal;

/**
 * Hasta cuantos metros de la sucursal se puede marcar, y como se mide.
 *
 * Es el mismo numero que usa la PWA ({@code DISTANCIA_MAXIMA_M} en
 * {@code deteccion-sucursal.util.ts}): si se cambia uno hay que cambiar el otro.
 * La app frena antes de mandar; esto es lo que hace que saltearse la app no sirva.
 */
public final class DistanciaMarcacion {

    /** Mas lejos que esto, en metros, no se marca. */
    public static final int DISTANCIA_MAXIMA_M = 80;

    private static final double RADIO_TIERRA_M = 6_371_000;

    private DistanciaMarcacion() {
    }

    /**
     * Lee el {@code "lat,lng"} de {@code sucursal.localizacion}. Null si no son dos numeros:
     * el dato se carga a mano, y medir contra un punto inventado es peor que no medir.
     */
    public static double[] coordenadas(String localizacion) {
        if (localizacion == null) return null;
        String[] partes = localizacion.split(",");
        if (partes.length != 2 || partes[0].trim().isEmpty() || partes[1].trim().isEmpty()) return null;
        try {
            double lat = Double.parseDouble(partes[0].trim());
            double lng = Double.parseDouble(partes[1].trim());
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) return null;
            return new double[]{lat, lng};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Si vino una posicion de verdad. El (0,0) es lo que manda un cliente sin GPS. */
    public static boolean tienePosicion(BigDecimal latitud, BigDecimal longitud) {
        return latitud != null && longitud != null
                && !(latitud.signum() == 0 && longitud.signum() == 0);
    }

    /** Distancia en metros, redondeada: la que se guarda y la que compara la app. Haversine. */
    public static int metros(double latA, double lngA, double latB, double lngB) {
        double dLat = Math.toRadians(latB - latA);
        double dLng = Math.toRadians(lngB - lngA);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.pow(Math.sin(dLng / 2), 2) * Math.cos(Math.toRadians(latA)) * Math.cos(Math.toRadians(latB));
        return (int) Math.round(RADIO_TIERRA_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    public static boolean estaLejos(int metros) {
        return metros > DISTANCIA_MAXIMA_M;
    }
}
