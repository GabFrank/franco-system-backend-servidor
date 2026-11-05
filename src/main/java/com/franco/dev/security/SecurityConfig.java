package com.franco.dev.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Collections;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private JwtAuthenticationProvider authenticationProvider;
    @Autowired
    private JwtAuthenticationEntryPoint entryPoint;

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(Collections.singletonList(authenticationProvider));
    }

    @Bean
    public JwtAuthenticationTokenFilter authenticationTokenFilter() {
        JwtAuthenticationTokenFilter filter = new JwtAuthenticationTokenFilter();
        filter.setAuthenticationManager(authenticationManager());
        filter.setAuthenticationSuccessHandler(new JwtSuccessHandler());
        return filter;
    }

    /**
     * Rutas que tienen que responder sin token. Cada una tiene un llamador conocido (issue #521):
     * <ul>
     *   <li>{@code /login}: login de desktop, PWA y app; {@code /login/biometric} y
     *       {@code /login/biometric-owner/*}: login biometrico de la app (frc-mobile).</li>
     *   <li>{@code /public/**}: foto de la muestra desde el telefono y recuperacion de contrasena.</li>
     *   <li>{@code /config/verificar}, {@code /config/hora-servidor}: se usan antes del login
     *       (configuracion inicial del desktop, reloj de marcacion). No exponen datos.</li>
     *   <li>{@code /config/sucursales}, {@code /config/all-sucursales}: la filial (de servidor a
     *       servidor, sin token), la configuracion del desktop y el pre-registro de la app. Devuelven
     *       solo id y nombre.</li>
     *   <li>{@code /config/pre-registro}: alta de funcionario desde la app, sin cuenta todavia.</li>
     *   <li>{@code /subscriptions/**} (GraphQL) y {@code /ws/gps/**} (GPS): websockets. El navegador
     *       no puede mandar el header Authorization en el handshake; autenticarlos queda pendiente.</li>
     *   <li>{@code /actuator/health}: el health check de {@code deploy.sh}. Si falla, hay rollback.</li>
     *   <li>{@code /error}: la pagina de error de Spring.</li>
     * </ul>
     */
    static final String[] RUTAS_PUBLICAS = {
            "/login", "/login/biometric", "/login/biometric-owner/*",
            "/public/**",
            "/config/verificar", "/config/hora-servidor",
            "/config/sucursales", "/config/all-sucursales",
            "/config/pre-registro",
            "/subscriptions/**", "/ws/gps/**",
            "/actuator/health", "/actuator/health/**",
            "/error"
    };

    /**
     * Deny by default (issue #521): lo que no esta en {@link #RUTAS_PUBLICAS} exige token. Antes
     * solo se cerraban rutas puntuales y {@code /notification/**} quedaba abierta: cualquiera podia
     * disparar notificaciones push sin login.
     *
     * <p>El token lo valida {@link JwtAuthenticationTokenFilter} en {@code /graphql/**},
     * {@code /api/**} y {@code /notification/**}. Una ruta nueva que necesite token tiene que
     * agregarse ahi tambien; si no, responde 401 aun con un token bueno.
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors().and().csrf().disable()
                .authorizeRequests()
                .antMatchers(RUTAS_PUBLICAS).permitAll()
                .anyRequest().authenticated()
                .and()
                .exceptionHandling().authenticationEntryPoint(entryPoint)
                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .addFilterBefore(authenticationTokenFilter(), UsernamePasswordAuthenticationFilter.class)
                .headers().cacheControl();
    }
}
