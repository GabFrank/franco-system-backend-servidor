package com.franco.dev.security;

import com.franco.dev.security.model.JwtAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.Filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Que ruta del central responde sin token y cual lo exige (issue #521). Levanta solo
 * SecurityConfig y un controller de prueba, sin base de datos.
 */
@SpringJUnitWebConfig(classes = SecurityConfigTest.Config.class, initializers = SecurityConfigTest.ProviderDePrueba.class)
class SecurityConfigTest {

    private static final String TOKEN_BUENO = "Token bueno";

    @Autowired
    private WebApplicationContext ctx;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx)
                .addFilters(ctx.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    // --- Rutas publicas: responden sin token ----------------------------------------------------

    @Test
    void losLoginsNoPidenToken() throws Exception {
        mvc.perform(post("/login")).andExpect(status().isOk());
        mvc.perform(post("/login/biometric")).andExpect(status().isOk());
        mvc.perform(get("/login/biometric-owner/abc-123")).andExpect(status().isOk());
    }

    @Test
    void elHealthDelDeployNoPideToken() throws Exception {
        // Si pide token, deploy.sh hace rollback.
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void lasRutasPublicasNoPidenToken() throws Exception {
        mvc.perform(get("/public/captura-muestra/tok")).andExpect(status().isOk());
        mvc.perform(post("/public/recuperacion-contrasena/verificar")).andExpect(status().isOk());
    }

    @Test
    void lasRutasDeConfigQueSeUsanAntesDelLoginNoPidenToken() throws Exception {
        mvc.perform(post("/config/verificar")).andExpect(status().isOk());
        mvc.perform(get("/config/hora-servidor")).andExpect(status().isOk());
        mvc.perform(get("/config/sucursales")).andExpect(status().isOk());      // la filial, sin token
        mvc.perform(get("/config/all-sucursales")).andExpect(status().isOk());
        mvc.perform(post("/config/pre-registro")).andExpect(status().isOk());
    }

    @Test
    void losWebsocketsNoPidenTokenEnElHandshake() throws Exception {
        mvc.perform(get("/subscriptions")).andExpect(status().isOk());
        mvc.perform(get("/ws/gps/info")).andExpect(status().isOk());   // SockJS
    }

    // --- Notificaciones: exigen token -----------------------------------------------------------

    @Test
    void lasNotificacionesSinTokenDan401() throws Exception {
        mvc.perform(post("/notification/gasto/1/2/3/1000")).andExpect(status().isUnauthorized());
        mvc.perform(post("/notification/venta-stock-critico")).andExpect(status().isUnauthorized());
        mvc.perform(post("/notification/token")).andExpect(status().isUnauthorized());
        mvc.perform(post("/notification/gasto/1/2/3/1000").header("Authorization", "Token malo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lasNotificacionesConTokenPasan() throws Exception {
        // Si /notification/** no estuviera en el filtro JWT, esto daria 401 aun con token.
        mvc.perform(post("/notification/gasto/1/2/3/1000").header("Authorization", TOKEN_BUENO))
                .andExpect(status().isOk());
        mvc.perform(post("/notification/venta-stock-critico").header("Authorization", TOKEN_BUENO))
                .andExpect(status().isOk());
    }

    // --- Lo que ya exigia token sigue igual -----------------------------------------------------

    @Test
    void graphqlYApiSinTokenDan401YConTokenPasan() throws Exception {
        mvc.perform(post("/graphql")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/version")).andExpect(status().isUnauthorized());
        mvc.perform(post("/graphql").header("Authorization", TOKEN_BUENO)).andExpect(status().isOk());
        mvc.perform(get("/api/version").header("Authorization", TOKEN_BUENO)).andExpect(status().isOk());
    }

    // --- Todo lo demas exige token --------------------------------------------------------------

    @Test
    void unaRutaSinReglaNoQuedaAbiertaPorDefecto() throws Exception {
        mvc.perform(get("/ruta-nueva")).andExpect(status().isUnauthorized());
        mvc.perform(get("/graphiql")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/config/otra-cosa")).andExpect(status().isUnauthorized());
        mvc.perform(get("/login/otra-cosa")).andExpect(status().isUnauthorized());
    }

    /**
     * Registra el provider ya armado: un singleton registrado asi no pasa por el autowiring, que
     * le pediria JwtValidator y UsuarioService (y detras, la base).
     */
    static class ProviderDePrueba implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            ctx.getBeanFactory().registerSingleton("authenticationProvider", providerQueAceptaSoloElTokenBueno());
        }
    }

    /** Acepta solo el token "bueno", como si JwtValidator lo hubiera validado. */
    private static JwtAuthenticationProvider providerQueAceptaSoloElTokenBueno() {
        JwtAuthenticationProvider provider = mock(JwtAuthenticationProvider.class);
        when(provider.supports(any())).thenReturn(true);
        when(provider.authenticate(any())).thenAnswer(inv -> {
            JwtAuthenticationToken t = inv.getArgument(0);
            if (!"bueno".equals(t.getToken())) throw new BadCredentialsException("token invalido");
            return new UsernamePasswordAuthenticationToken(
                    "usuario", null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        });
        return provider;
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {

        @Bean
        JwtAuthenticationEntryPoint entryPoint() {
            return new JwtAuthenticationEntryPoint();
        }

        @Bean
        RutasDePrueba rutasDePrueba() {
            return new RutasDePrueba();
        }
    }

    /** Responde 200 en todas las rutas: el test mide solo lo que decide la seguridad. */
    @RestController
    static class RutasDePrueba {
        @RequestMapping({"/login", "/login/biometric", "/login/biometric-owner/*", "/login/otra-cosa",
                "/public/**", "/config/**", "/subscriptions", "/ws/gps/**",
                "/actuator/health", "/actuator/info", "/notification/**",
                "/graphql", "/graphiql", "/api/version", "/ruta-nueva"})
        String ok() {
            return "ok";
        }
    }
}
