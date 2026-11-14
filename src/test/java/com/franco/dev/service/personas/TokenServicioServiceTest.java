package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.TokenServicio;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.personas.TokenServicioRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.security.TokenServicioFormato;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Emision y revocacion de tokens de servicio (issue #579, fase 1). */
class TokenServicioServiceTest {

    private static final Long BOT_ID = 900L;

    private TokenServicioRepository repository;
    private UsuarioRepository usuarioRepository;
    private UsuarioService usuarioService;
    private Usuario bot;

    @BeforeEach
    void setUp() {
        repository = mock(TokenServicioRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        usuarioService = mock(UsuarioService.class);
        when(repository.save(any(TokenServicio.class))).thenAnswer(inv -> {
            TokenServicio t = inv.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            return t;
        });
        bot = new Usuario();
        bot.setId(BOT_ID);
        bot.setNickname("BOT-OPERACIONES");
        bot.setPassword("123");
        bot.setActivo(true);
        when(usuarioService.findById(BOT_ID)).thenReturn(Optional.of(bot));
    }

    private TokenServicioService service(TesoreriaSecurityService seg) {
        return new TokenServicioService(repository, usuarioRepository, usuarioService, seg);
    }

    private TokenServicioService comoAdmin() {
        return service(new TesoreriaSecurityFalso(1L, "ADMIN"));
    }

    @Test
    void emiteUnTokenConFormatoYGuardaSoloElHash() {
        TokenServicioService.TokenServicioCreado emitido = comoAdmin().crear(BOT_ID, "loom-operaciones", null);

        String token = emitido.getToken();
        assertTrue(token.startsWith("frcsvc_"));
        assertEquals(7 + 43, token.length(), "frcsvc_ + 32 bytes en base64url sin padding");

        TokenServicio t = emitido.getTokenServicio();
        assertEquals(TokenServicioFormato.hash(token), t.getHash());
        assertNotEquals(token, t.getHash());
        assertEquals(64, t.getHash().length());
        assertEquals(token.substring(0, 12), t.getPrefijo());
        assertSame(bot, t.getUsuario());
        assertEquals(1L, t.getCreadoPor().getId());
        assertNull(t.getRevocadoEn());
        assertTrue(t.getVigente());
    }

    @Test
    void dosEmisionesDanTokensDistintos() {
        TokenServicioService s = comoAdmin();
        assertNotEquals(s.crear(BOT_ID, "uno", null).getToken(), s.crear(BOT_ID, "dos", null).getToken());
    }

    @Test
    void vigenciaPorDefectoEs90DiasYRespetaLaPedida() {
        LocalDateTime antes = LocalDateTime.now();
        TokenServicio porDefecto = comoAdmin().crear(BOT_ID, "por-defecto", null).getTokenServicio();
        assertFalse(porDefecto.getExpiraEn().isBefore(antes.plusDays(90)));
        assertTrue(porDefecto.getExpiraEn().isBefore(antes.plusDays(90).plusMinutes(1)));

        TokenServicio pedido = comoAdmin().crear(BOT_ID, "pedido", 7).getTokenServicio();
        assertTrue(pedido.getExpiraEn().isBefore(antes.plusDays(7).plusMinutes(1)));
    }

    @Test
    void rechazaVigenciaFueraDeRango() {
        assertThrows(GraphQLException.class, () -> comoAdmin().crear(BOT_ID, "cero", 0));
        assertThrows(GraphQLException.class, () -> comoAdmin().crear(BOT_ID, "mucho", 366));
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaNombreVacioOLargo() {
        assertThrows(GraphQLException.class, () -> comoAdmin().crear(BOT_ID, "  a ", null));
        assertThrows(GraphQLException.class, () -> comoAdmin().crear(BOT_ID, null, null));
        assertThrows(GraphQLException.class, () -> comoAdmin().crear(BOT_ID, "x".repeat(81), null));
    }

    @Test
    void rechazaUsuarioInexistenteOInactivo() {
        when(usuarioService.findById(5L)).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, () -> comoAdmin().crear(5L, "nadie", null));

        bot.setActivo(false);
        assertThrows(GraphQLException.class, () -> comoAdmin().crear(BOT_ID, "inactivo", null));
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaSuperusuarioPorNicknameSinImportarMayusculasNiEspacios() {
        bot.setNickname(" admin ");
        assertThrows(GraphQLException.class, () -> comoAdmin().crear(BOT_ID, "llave-maestra", null));
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaSuperusuarioPorRol() {
        when(usuarioService.tieneRol(eq(BOT_ID), eq("ADMIN"))).thenReturn(true);
        assertThrows(GraphQLException.class, () -> comoAdmin().crear(BOT_ID, "llave-maestra", null));
        verify(repository, never()).save(any());
    }

    @Test
    void reemplazaLaClaveDelUsuarioParaCerrarSuLoginHumano() {
        comoAdmin().crear(BOT_ID, "loom", null);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        String clave = guardado.getValue().getPassword();
        assertNotEquals("123", clave);
        assertTrue(clave.length() >= 40);
        assertEquals(clave.toUpperCase(), clave, "el login compara en mayusculas");
    }

    @Test
    void sinSuperusuarioNoEmiteNiRevocaNiLista() {
        TokenServicioService s = service(new TesoreriaSecurityFalso(2L, "TESORERIA GESTIONAR"));
        assertThrows(GraphQLException.class, () -> s.crear(BOT_ID, "loom", null));
        assertThrows(GraphQLException.class, () -> s.revocar(1L));
        assertThrows(GraphQLException.class, () -> s.listar(null));
        verifyNoInteractions(repository, usuarioRepository);
        verify(usuarioService, never()).findById(any());
    }

    @Test
    void revocarMarcaFechaYQuienYEsIdempotente() {
        TokenServicio t = new TokenServicio();
        t.setId(1L);
        t.setUsuario(bot);
        t.setExpiraEn(LocalDateTime.now().plusDays(30));
        when(repository.findById(1L)).thenReturn(Optional.of(t));

        assertTrue(comoAdmin().revocar(1L));
        LocalDateTime revocado = t.getRevocadoEn();
        assertNotNull(revocado);
        assertEquals(1L, t.getRevocadoPor().getId());
        assertFalse(t.getVigente());

        assertTrue(service(new TesoreriaSecurityFalso(3L, "ADMIN")).revocar(1L));
        assertEquals(revocado, t.getRevocadoEn());
        assertEquals(1L, t.getRevocadoPor().getId());
        verify(repository, times(1)).save(t);
    }

    @Test
    void revocarUnoInexistenteFalla() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, () -> comoAdmin().revocar(9L));
    }

    @Test
    void unTokenVencidoNoEstaVigente() {
        TokenServicio t = new TokenServicio();
        t.setExpiraEn(LocalDateTime.now().minusMinutes(1));
        assertFalse(t.getVigente());
    }
}
