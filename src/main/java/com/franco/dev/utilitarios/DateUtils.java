package com.franco.dev.utilitarios;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public class DateUtils {

    private static final String PATTERN = "yyyy-MM-dd HH:mm";
    private static final String PATTERN_ONLY_DATE = "yyyy-MM-dd";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN);
    private static final DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern(PATTERN_ONLY_DATE);

    public static String toString(LocalDateTime d) {
        return formatter.format(d);
    }
    public static String dateToString(LocalDateTime d) {
        return formatter.format(d);
    }

    public static String toStringOnlyDate(LocalDateTime d) {
        return formatter2.format(d);
    }

    public static LocalDateTime stringToDate(String s) {
        if(s == null) return null;
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

    public static String dateToStringWithFormat(LocalDateTime date, String format){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return formatter.format(date);
    }

}
