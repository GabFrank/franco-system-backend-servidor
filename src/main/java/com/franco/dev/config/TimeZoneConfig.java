package com.franco.dev.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

/**
 * Alinea la zona por defecto de la JVM con {@code app.timezone} para que
 * {@code LocalDateTime.now()} y el endpoint {@code /config/hora-servidor} reflejen la hora civil esperada.
 * El instante {@code System.currentTimeMillis()} sigue siendo el del reloj del sistema operativo.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TimeZoneConfig {

    @Value("${app.timezone:America/Asuncion}")
    private String timezoneId;

    @PostConstruct
    public void applyDefaultTimeZone() {
        TimeZone tz = TimeZone.getTimeZone(timezoneId.trim());
        TimeZone.setDefault(tz);
        System.setProperty("user.timezone", tz.getID());
    }
}
