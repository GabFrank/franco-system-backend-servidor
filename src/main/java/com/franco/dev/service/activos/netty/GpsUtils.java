package com.franco.dev.service.activos.netty;

public class GpsUtils {

    public static Double convertCoordinates(String rawCoordinate, String direction) {
        if (rawCoordinate == null || rawCoordinate.isEmpty()) {
            return 0.0;
        }

        try {
            double rawVal = Double.parseDouble(rawCoordinate);
            int degrees = (int) (rawVal / 100);
            double minutes = rawVal - (degrees * 100);
            double decimalDegrees = degrees + (minutes / 60);

            if ("S".equalsIgnoreCase(direction) || "W".equalsIgnoreCase(direction) || "O".equalsIgnoreCase(direction)) {
                decimalDegrees = decimalDegrees * -1;
            }

            return decimalDegrees;
        } catch (NumberFormatException e) {
            System.err.println("Error parseando coordenada: " + rawCoordinate);
            return 0.0;
        }
    }
}
