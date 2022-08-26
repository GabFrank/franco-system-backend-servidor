package com.franco.dev.utilitarios;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class DateUtils {

    private static final String PATTERN = "yyyy-MM-dd HH:mm";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN);

    public static String toString(LocalDateTime d) {
        return formatter.format(d);
    }

    public static LocalDateTime toDate(String s) {
            return LocalDateTime.parse(s, formatter);
    }

    public static LocalDateTime getFirstDayOfMonth(long offsetMonth){
        LocalDateTime hoy = LocalDateTime.now().withHour(00).withMinute(00).withSecond(00).plusMonths(offsetMonth);
        return hoy.with(TemporalAdjusters.firstDayOfMonth());
    }

    public static LocalDateTime getLastDayOfMonth(long offsetMonth){
        LocalDateTime hoy = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).plusMonths(offsetMonth);
        return hoy.with(TemporalAdjusters.lastDayOfMonth());
    }

}
