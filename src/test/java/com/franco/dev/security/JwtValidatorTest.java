package com.franco.dev.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtValidatorTest {

    private static final String SECRET = "Graphql";

    private final JwtValidator validator = new JwtValidator();

    /** Token como lo emite el JwtGenerator del central: subject + claim "nickname". */
    private String tokenDelCentral(String nickname) {
        Claims claims = Jwts.claims().setSubject(nickname);
        claims.put("nickname", nickname);
        claims.put("password", "x");
        return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, SECRET).compact();
    }

    /** Token como lo emite el JwtGenerator del filial: solo subject, sin claim "nickname". */
    private String tokenDelFilial(String nickname) {
        Claims claims = Jwts.claims().setSubject(nickname);
        claims.put("password", "x");
        return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, SECRET).compact();
    }

    @Test
    void leeElClaimNicknameCuandoEstaPresente() {
        assertEquals("MR", validator.validate(tokenDelCentral("MR")).getNickname());
    }

    @Test
    void caeAlSubjectCuandoNoHayClaimNickname() {
        // El JwtGenerator del filial nunca escribe el claim "nickname". Sin este
        // fallback el principal quedaba null y los servicios que resuelven al
        // usuario por SecurityContext (RrhhSecurityService, TesoreriaSecurityService)
        // reportaban falta de permisos aunque el usuario tuviera los roles.
        assertEquals("mr", validator.validate(tokenDelFilial("mr")).getNickname());
    }

    @Test
    void caeAlSubjectCuandoElClaimNicknameEstaVacio() {
        Claims claims = Jwts.claims().setSubject("MR");
        claims.put("nickname", "   ");
        String token = Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, SECRET).compact();

        assertEquals("MR", validator.validate(token).getNickname());
    }

    @Test
    void nicknameNuloSiElTokenNoTraeNiClaimNiSubject() {
        Claims claims = Jwts.claims();
        claims.put("password", "x");
        String token = Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, SECRET).compact();

        assertNull(validator.validate(token).getNickname());
    }
}
