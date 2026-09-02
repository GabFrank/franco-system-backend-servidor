package com.franco.dev.security;

import com.franco.dev.domain.personas.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

@Component
public class JwtValidator {


    private String secret = "Graphql";

    public JwtUser validate(String token) {

        JwtUser jwtUser = null;
        try {
            Claims body = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();

            jwtUser = new JwtUser();

            jwtUser.setNickname(resolveNickname(body));
            jwtUser.setPassword((String) body.get("password"));
        }
        catch (Exception e) {
            System.out.println(e);
        }

        return jwtUser;
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
