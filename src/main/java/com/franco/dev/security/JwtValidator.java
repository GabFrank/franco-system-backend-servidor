package com.franco.dev.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    private final JwtClaves claves;

    public JwtValidator(JwtClaves claves) {
        this.claves = claves;
    }

    /** El usuario del token, o null si ninguna clave aceptada valida la firma. Nunca lanza. */
    public JwtUser validate(String token) {
        if (token == null) return null;
        for (byte[] clave : claves.aceptadas()) {
            Claims body;
            try {
                body = Jwts.parser().setSigningKey(clave).parseClaimsJws(token).getBody();
            } catch (RuntimeException e) {
                continue;
            }
            JwtUser jwtUser = new JwtUser();
            jwtUser.setNickname(resolveNickname(body));
            return jwtUser;
        }
        log.warn("Token de sesion rechazado: ninguna clave aceptada valida la firma.");
        return null;
    }

    /**
     * El nickname del claim dedicado, con fallback al subject del JWT.
     *
     * El JwtGenerator del filial solo setea el subject y nunca escribe el claim
     * "nickname". Leyendo unicamente el claim, un token emitido por el filial
     * dejaba el principal en null, y todo lo que resuelve al usuario autenticado
     * por SecurityContext (RrhhSecurityService, TesoreriaSecurityService) lo veia
     * como no autenticado: reportaba falta de permisos aunque el usuario tuviera
     * los roles, incluso ADMIN. El desktop cae al token del filial cuando
     * token_central no esta seteado (ver login.service.ts / graphql-connection.service.ts).
     *
     * El fallback tambien cubre los tokens ya emitidos: estos JWT no llevan
     * expiracion, asi que sin esto los usuarios seguirian bloqueados hasta
     * volver a loguearse.
     */
    private String resolveNickname(Claims body) {
        String nickname = (String) body.get("nickname");
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = body.getSubject();
        }
        return nickname;
    }
}
