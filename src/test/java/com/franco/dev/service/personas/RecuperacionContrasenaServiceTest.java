package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.RecuperacionContrasena;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.EstadoRecuperacionContrasena;
import com.franco.dev.domain.personas.enums.TipoRecuperacionContrasena;
import com.franco.dev.repository.personas.RecuperacionContrasenaRepository;
import com.franco.dev.service.personas.RecuperacionContrasenaService.EstadoPublico;
import com.franco.dev.service.personas.RecuperacionContrasenaService.RecuperacionException;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Lo que hace segura la recuperacion de contrasena: captcha, bloqueo por intentos, codigos de
 * pocos intentos, RRHH que no se aprueba a si mismo y la contrasena que no se cambia sin la
 * aprobacion.
 */
class RecuperacionContrasenaServiceTest {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private RecuperacionContrasenaRepository repository;
    private UsuarioService usuarioService;
    private CaptchaService captcha;
    private RrhhSecurityService seguridad;
    private JdbcTemplate jdbc;
    private RecuperacionContrasenaService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(RecuperacionContrasenaRepository.class);
        usuarioService = mock(UsuarioService.class);
        captcha = mock(CaptchaService.class);
        seguridad = mock(RrhhSecurityService.class);
        jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JavaMailSender> mail = mock(ObjectProvider.class);
        service = new RecuperacionContrasenaService(repository, usuarioService, captcha, seguridad, jdbc, mail);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Usuario usuario(long id, String nickname, String documento) {
        Persona p = new Persona();
        p.setDocumento(documento);
        p.setNombre("JUAN PEREZ");
        Usuario u = new Usuario();
        u.setId(id);
        u.setNickname(nickname);
        u.setPersona(p);
        return u;
    }

    private RecuperacionContrasena solicitud(TipoRecuperacionContrasena tipo, EstadoRecuperacionContrasena estado,
                                             String codigo) {
        RecuperacionContrasena r = new RecuperacionContrasena();
        r.setId(10L);
        r.setUsuario(usuario(7L, "JPEREZ", "4.567.890"));
        r.setTipo(tipo);
        r.setEstado(estado);
        r.setCodigoHash(BCRYPT.encode(codigo));
        r.setIntentos(0);
        r.setVenceEn(LocalDateTime.now().plusMinutes(10));
        when(repository.findById(10L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void conCaptchaIncorrectoNoSeConsultaNada() {
        when(captcha.validar("c1", "XXXXX")).thenReturn(false);
        RecuperacionException e = assertThrows(RecuperacionException.class, () -> service.verificarIdentidad(
                "jperez", "4567890", LocalDate.of(2000, 1, 1), "c1", "XXXXX", "10.0.0.1"));
        assertTrue(e.getMessage().contains("imagen"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void conCincoFallosRecientesElUsuarioQuedaBloqueado() {
        when(captcha.validar(any(), any())).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(5L);
        RecuperacionException e = assertThrows(RecuperacionException.class, () -> service.verificarIdentidad(
                "jperez", "4567890", LocalDate.of(2000, 1, 1), "c1", "ABCDE", "10.0.0.1"));
        assertTrue(e.getMessage().contains("Demasiados intentos"));
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(), any(), any());
    }

    @Test
    void datosQueNoCoincidenSeRegistranComoFalloConMensajeGenerico() {
        when(captcha.validar(any(), any())).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(Collections.emptyList());
        RecuperacionException e = assertThrows(RecuperacionException.class, () -> service.verificarIdentidad(
                " jperez ", "4.567.890", LocalDate.of(2000, 1, 1), "c1", "ABCDE", "10.0.0.1"));
        assertTrue(e.getMessage().contains("no coinciden"));
        verify(jdbc).update(anyString(), eq("JPEREZ"), eq("10.0.0.1"), eq(false));
    }

    @Test
    void elCodigoDelCorreoCorrectoHabilitaElCambio() {
        solicitud(TipoRecuperacionContrasena.CORREO, EstadoRecuperacionContrasena.PENDIENTE, "123456");
        EstadoPublico e = service.consultar(10L, "123456", null);
        assertTrue(e.puedeRestablecer);
    }

    @Test
    void cincoCodigosEquivocadosAnulanLaSolicitud() {
        RecuperacionContrasena r = solicitud(TipoRecuperacionContrasena.CORREO, EstadoRecuperacionContrasena.PENDIENTE, "123456");
        for (int i = 0; i < 5; i++) {
            assertThrows(RecuperacionException.class, () -> service.consultar(10L, "000000", null));
        }
        assertEquals(EstadoRecuperacionContrasena.ANULADA, r.getEstado());
        assertThrows(RecuperacionException.class, () -> service.consultar(10L, "123456", null));
    }

    @Test
    void elCodigoVencidoNoPermiteRestablecer() {
        RecuperacionContrasena r = solicitud(TipoRecuperacionContrasena.CORREO, EstadoRecuperacionContrasena.PENDIENTE, "123456");
        r.setVenceEn(LocalDateTime.now().minusMinutes(1));
        RecuperacionException e = assertThrows(RecuperacionException.class,
                () -> service.restablecer(10L, "123456", null, "NuevaClave1"));
        assertTrue(e.getMessage().contains("venció"));
        verify(usuarioService, never()).save(any(Usuario.class));
    }

    @Test
    void sinAprobacionDeRrhhNoSeCambiaLaContrasena() {
        solicitud(TipoRecuperacionContrasena.RRHH, EstadoRecuperacionContrasena.PENDIENTE, "ABCD2345");
        EstadoPublico e = service.consultar(10L, "abcd-2345", "4567890");
        assertFalse(e.puedeRestablecer);
        assertThrows(RecuperacionException.class, () -> service.restablecer(10L, "ABCD-2345", "4567890", "NuevaClave1"));
        verify(usuarioService, never()).save(any(Usuario.class));
    }

    @Test
    void laSolicitudDeRrhhExigeLaCedulaDelUsuario() {
        RecuperacionContrasena r = solicitud(TipoRecuperacionContrasena.RRHH, EstadoRecuperacionContrasena.APROBADA, "ABCD2345");
        assertThrows(RecuperacionException.class, () -> service.consultar(10L, "ABCD-2345", "1111111"));
        assertEquals(1, r.getIntentos());
    }

    @Test
    void aprobadaYConCodigoSecretoSeCambiaLaContrasenaYGuardaElCorreo() {
        RecuperacionContrasena r = solicitud(TipoRecuperacionContrasena.RRHH, EstadoRecuperacionContrasena.APROBADA, "ABCD2345");
        r.setEmail("juan@example.com");
        when(usuarioService.existsByEmail("juan@example.com")).thenReturn(false);

        service.restablecer(10L, "ABCD-2345", "4.567.890", "NuevaClave1");

        assertEquals("NuevaClave1", r.getUsuario().getPassword());
        assertEquals("juan@example.com", r.getUsuario().getEmail());
        assertEquals(EstadoRecuperacionContrasena.COMPLETADA, r.getEstado());
        verify(usuarioService).save(r.getUsuario());
        verify(repository).anularAbiertas(7L, 10L);
    }

    @Test
    void laContrasenaNoPuedeSerElUsuarioNiLaCedula() {
        solicitud(TipoRecuperacionContrasena.CORREO, EstadoRecuperacionContrasena.PENDIENTE, "123456");
        assertThrows(RecuperacionException.class, () -> service.restablecer(10L, "123456", null, "jperez"));
        assertThrows(RecuperacionException.class, () -> service.restablecer(10L, "123456", null, "4567890"));
        assertThrows(RecuperacionException.class, () -> service.restablecer(10L, "123456", null, "12345"));
        verify(usuarioService, never()).save(any(Usuario.class));
    }

    @Test
    void rrhhNoPuedeAprobarSuPropiaSolicitud() {
        solicitud(TipoRecuperacionContrasena.RRHH, EstadoRecuperacionContrasena.PENDIENTE, "ABCD2345");
        when(seguridad.currentUsuario()).thenReturn(usuario(7L, "JPEREZ", "4567890"));
        assertThrows(GraphQLException.class, () -> service.aprobar(10L));
    }

    @Test
    void aprobarDaVeinticuatroHorasParaUsarla() {
        RecuperacionContrasena r = solicitud(TipoRecuperacionContrasena.RRHH, EstadoRecuperacionContrasena.PENDIENTE, "ABCD2345");
        when(seguridad.currentUsuario()).thenReturn(usuario(99L, "RRHH1", "1234567"));
        service.aprobar(10L);
        assertEquals(EstadoRecuperacionContrasena.APROBADA, r.getEstado());
        assertTrue(r.getVenceEn().isAfter(LocalDateTime.now().plusHours(23)));
        assertEquals(99L, r.getResueltoPor().getId());
    }

    @Test
    void elCorreoSeMuestraEnmascarado() {
        assertEquals("fr•••@gmail.com", RecuperacionContrasenaService.enmascarar("francoarevalos@gmail.com"));
        assertEquals("a•••@x.com", RecuperacionContrasenaService.enmascarar("ab@x.com"));
    }
}
