package com.franco.dev.config.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Con {@code GMAIL_CLIENT_ID}, {@code GMAIL_CLIENT_SECRET} y {@code GMAIL_REFRESH_TOKEN} cargados,
 * los correos salen por la API de Gmail en vez de SMTP (ver {@link GmailApiMailSender}). Este bean ocupa el lugar del {@link JavaMailSender} que arma
 * Spring Boot; si falta alguno no se crea y todo sigue por SMTP como antes.
 */
@Slf4j
@Configuration
@ConditionalOnExpression("!'${gmail.api.refresh-token:}'.trim().isEmpty()"
        + " && !'${gmail.api.client-id:}'.trim().isEmpty()"
        + " && !'${gmail.api.client-secret:}'.trim().isEmpty()")
public class GmailApiMailConfig {

    @Bean
    public JavaMailSender gmailApiMailSender(@Value("${gmail.api.client-id:}") String clientId,
                                             @Value("${gmail.api.client-secret:}") String clientSecret,
                                             @Value("${gmail.api.refresh-token:}") String refreshToken) {
        log.info("Correo: se envia por la API de Gmail (HTTPS), no por SMTP");
        return new GmailApiMailSender(clientId, clientSecret, refreshToken);
    }
}
