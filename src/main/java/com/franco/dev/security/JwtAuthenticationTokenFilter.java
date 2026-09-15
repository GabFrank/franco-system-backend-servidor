package com.franco.dev.security;

import com.franco.dev.security.model.JwtAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtAuthenticationTokenFilter extends AbstractAuthenticationProcessingFilter {

    /**
     * Las rutas que este filtro autentica.
     *
     * <p><b>{@code /api/**} estaba declarado {@code .authenticated()} en SecurityConfig pero
     * ningun filtro lo procesaba</b>, asi que devolvia 401 siempre --tambien con un token bueno--
     * y los seis controllers que cuelgan de ahi eran inalcanzables desde la app. Se descubrio el
     * 2026-09-15 sirviendo la foto de una muestra: el mismo token daba 200 en {@code /graphql} y
     * 401 en {@code /api}.
     *
     * <p>El cambio es aditivo: una peticion sin header sigue terminando en 401, igual que antes.
     * Lo unico que cambia es que ahora una CON token valido pasa.
     *
     * <p>{@code /public/**} y {@code /login} quedan afuera a proposito: son {@code permitAll} y
     * ahi no hay token que exigir --es por donde entra el telefono que sube la foto del cupon--.
     */
    private static final RequestMatcher RUTAS = new OrRequestMatcher(
            new AntPathRequestMatcher("/graphql/**"),
            new AntPathRequestMatcher("/api/**"));

    public JwtAuthenticationTokenFilter() {
        super(RUTAS);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) throws AuthenticationException, IOException, ServletException {

        String header = httpServletRequest.getHeader("Authorization");

        if (header == null || !header.startsWith("Token ")) {
            // No devolver null: AbstractAuthenticationProcessingFilter interpreta el null
            // como "autenticacion aun sin terminar", corta el doFilter y NO escribe nada
            // en la respuesta. El cliente recibe un HTTP 200 con cuerpo vacio, que Apollo
            // no puede parsear. Lanzando la excepcion se responde el 401 que corresponde.
            throw new InsufficientAuthenticationException("Falta el token de autenticacion");
        }

        String authenticationToken = header.substring(6);

        JwtAuthenticationToken token = new JwtAuthenticationToken(authenticationToken);
        return getAuthenticationManager().authenticate(token);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult);
        chain.doFilter(request, response);
    }
}
