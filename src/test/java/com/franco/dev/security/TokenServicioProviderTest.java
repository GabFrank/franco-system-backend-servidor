package com.franco.dev.security;

import com.franco.dev.security.model.JwtAuthenticationToken;
import com.franco.dev.security.model.JwtUserDetails;
import com.franco.dev.service.personas.UsuarioService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * La bifurcacion de JwtAuthenticationProvider entre el JWT de sesion y el token de servicio de un bot
 * (issue #579). Las condiciones del SQL (revocado, vencido, inactivo, superusuario) se prueban
 * contra PostgreSQL real en TokenServicioSqlIT.
 */
class TokenServicioProviderTest {

    private static final String LEGACY = "Graphql";

    private JdbcTemplate jdbc;
    private JwtValidator jwtValidator;
    private TokenServicioValidator servicioValidator;
    private JwtAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        jwtValidator = spy(new JwtValidator(new JwtClaves(LEGACY, "", "true")));
        servicioValidator = spy(new TokenServicioValidator(jdbc));
        provider = new JwtAuthenticationProvider(mock(UsuarioService.class));
        ReflectionTestUtils.setField(provider, "validator", jwtValidator);
        ReflectionTestUtils.setField(provider, "tokenServicioValidator", servicioValidator);
    }

    @SuppressWarnings("unchecked")
    private void laBaseDevuelve(List<TokenServicioValidator.Valido> filas) {
        doReturn(filas).when(jdbc).query(eq(TokenServicioValidator.SQL_VALIDAR), any(RowMapper.class), anyString());
    }

    private static String jwt(String nickname) {
        Claims claims = Jwts.claims().setSubject(nickname);
        claims.put("nickname", nickname);
        return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, LEGACY).compact();
    }

    private Authentication autenticar(String token) {
        return provider.authenticate(new JwtAuthenticationToken(token));
    }

    @Test
    void unTokenDeServicioValidoAutenticaConElNicknameDeSuUsuarioYSinGuardarElToken() {
        String token = TokenServicioFormato.generar();
        laBaseDevuelve(Collections.singletonList(new TokenServicioValidator.Valido(7L, "BOT-OPERACIONES")));

        Authentication auth = autenticar(token);

        assertEquals("BOT-OPERACIONES", auth.getName());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> TokenServicioFormato.AUTORIDAD_SESION.equals(a.getAuthority())));
        JwtUserDetails detalle = (JwtUserDetails) auth.getPrincipal();
        assertNull(detalle.getPassword());
        assertNotEquals(token, ReflectionTestUtils.getField(detalle, "token"));
        verify(jdbc).query(eq(TokenServicioValidator.SQL_VALIDAR), any(RowMapper.class), eq(TokenServicioFormato.hash(token)));
        verify(jdbc).update(TokenServicioValidator.SQL_ULTIMO_USO, 7L);
        verify(jwtValidator, never()).validate(any());
    }

    @Test
    void unTokenDeServicioSinFilaDa401YNoCaeAlValidadorDeJwt() {
        laBaseDevuelve(Collections.emptyList());
        assertThrows(BadCredentialsException.class, () -> autenticar(TokenServicioFormato.generar()));
        verify(jwtValidator, never()).validate(any());
        verify(jdbc, never()).update(eq(TokenServicioValidator.SQL_ULTIMO_USO), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unErrorDeBaseNoAutenticaYSaleComoAuthenticationException() {
        when(jdbc.query(anyString(), any(RowMapper.class), anyString()))
                .thenThrow(new DataAccessResourceFailureException("pool agotado"));
        AuthenticationException e = assertThrows(AuthenticationException.class,
                () -> autenticar(TokenServicioFormato.generar()));
        assertTrue(e instanceof InternalAuthenticationServiceException);
    }

    @Test
    void siFallaRegistrarElUsoElRequestIgualAutentica() {
        laBaseDevuelve(Collections.singletonList(new TokenServicioValidator.Valido(7L, "BOT")));
        when(jdbc.update(eq(TokenServicioValidator.SQL_ULTIMO_USO), anyLong()))
                .thenThrow(new DataAccessResourceFailureException("lock"));
        assertEquals("BOT", autenticar(TokenServicioFormato.generar()).getName());
    }

    @Test
    void unJwtDeSesionSigueYendoPorElValidadorDeJwt() {
        Authentication auth = autenticar(jwt("MR"));
        assertEquals("MR", auth.getName());
        assertTrue(auth.getAuthorities().stream()
                .noneMatch(a -> TokenServicioFormato.AUTORIDAD_SESION.equals(a.getAuthority())));
        verifyNoInteractions(jdbc);
        verify(servicioValidator, never()).validar(any());
    }

    @Test
    void unJwtInvalidoSigueDando401() {
        assertThrows(BadCredentialsException.class, () -> autenticar("eyJbasura.basura.basura"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void elPrefijoEsSensibleAMayusculas() {
        assertFalse(TokenServicioFormato.esTokenDeServicio("FRCSVC_abc"));
        assertThrows(BadCredentialsException.class, () -> autenticar("FRCSVC_abc"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void elSqlExigeTodasLasCondiciones() {
        String sql = TokenServicioValidator.SQL_VALIDAR;
        assertTrue(sql.contains("t.revocado_en IS NULL"));
        assertTrue(sql.contains("t.expira_en > now()"));
        assertTrue(sql.contains("u.activo IS NOT FALSE"));
        assertTrue(sql.contains("upper(trim(u.nickname)) <> 'ADMIN'"));
        assertTrue(sql.contains("upper(trim(r.nombre)) = 'ADMIN'"));
        // El dueno del rol es user_id; usuario_id es quien lo asigno (RoleRepository.findByUsuarioId).
        assertTrue(sql.contains("ur.user_id = u.id"));
        assertTrue(TokenServicioValidator.SQL_ULTIMO_USO.contains("SKIP LOCKED"));
    }
}
