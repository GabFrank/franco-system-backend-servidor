package com.franco.dev.utilitarios;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public class DateUtils {

    private static final String PATTERN = "yyyy-MM-dd HH:mm";
    private static final String PATTERN_ISO = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String PATTERN_ONLY_DATE = "yyyy-MM-dd";
    private static final String PATTERN_FULL = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN);
    private static final DateTimeFormatter formatter_iso = DateTimeFormatter.ofPattern(PATTERN_ISO);
    private static final DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern(PATTERN_ONLY_DATE);
    private static final DateTimeFormatter formatter_full = DateTimeFormatter.ofPattern(PATTERN_FULL);

    public static String toString(LocalDateTime d) {
        return formatter.format(d);
    }

    public static String dateToString(LocalDateTime d) {
        return formatter.format(d);
    }

    public static String toStringOnlyDate(LocalDateTime d) {
        return formatter2.format(d);
    }

    /**
     * Convierte un parametro de fecha de GraphQL a LocalDate, tolerando null.
     * Lo usan los filtros paginados: un filtro vacio tiene que llegar como null al JPQL.
     */
    public static LocalDate stringToLocalDate(String s) {
        LocalDateTime d = stringToDate(s);
        return d != null ? d.toLocalDate() : null;
    }

    public static LocalDateTime stringToDate(String s) {
        if (s == null)
            return null;
        s = s.trim();
        if (s.isEmpty())
            return null;
        if (s.contains("T")) {
            // ISO-8601 con zona/offset (ej. "2026-06-13T19:39:04.456Z" o "...-03:00"),
            // milisegundos opcionales. Se convierte al huso horario del servidor.
            try {
                return java.time.OffsetDateTime.parse(s)
                        .atZoneSameInstant(java.time.ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (java.time.format.DateTimeParseException ignored) {
                // ISO local sin zona, con fracciones de segundo opcionales
                return LocalDateTime.parse(s);
            }
        }
        if (s.length() == 10) {
            return LocalDateTime.parse(s + " 00:00", formatter);
        }
        if (s.length() == 19) {
            return LocalDateTime.parse(s, formatter_full);
        }
        return LocalDateTime.parse(s, formatter);
    }

    public static LocalDateTime getFirstDayOfMonth(long offsetMonth) {
        LocalDateTime hoy = LocalDateTime.now().withHour(00).withMinute(00).withSecond(00).plusMonths(offsetMonth);
        return hoy.with(TemporalAdjusters.firstDayOfMonth());
    }

    public static LocalDateTime getLastDayOfMonth(long offsetMonth) {
        LocalDateTime hoy = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).plusMonths(offsetMonth);
        return hoy.with(TemporalAdjusters.lastDayOfMonth());
    }

    public static String dateToStringWithFormat(LocalDateTime date, String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return formatter.format(date);
    }

    public static LocalDateTime stringToDateEndOfDay(String s) {
        if (s == null)
            return null;
        LocalDateTime date = stringToDate(s);
        return date.withHour(23).withMinute(59).withSecond(59);
    }

    /**
     * Los gráficos envían fin de mes como {@code 23:59} (sin segundos); el parser deja
     * {@code 23:59:00} y excluye ventas del último minuto. Ajusta a fin de día inclusivo.
     */
    public static LocalDateTime ajustarFinRangoGrafico(String raw, LocalDateTime fin) {
        if (fin == null) {
            return null;
        }
        boolean finSinSegundos = raw != null
                && (raw.length() == 16 || raw.length() == 19)
                && raw.contains("23:59")
                && (raw.length() == 16 || raw.endsWith("23:59:00"));
        if (finSinSegundos || (fin.getHour() == 23 && fin.getMinute() == 59 && fin.getSecond() == 0)) {
            return fin.withSecond(59);
        }
        return fin;
    }

}
