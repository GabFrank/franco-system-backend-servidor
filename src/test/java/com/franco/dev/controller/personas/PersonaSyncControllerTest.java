package com.franco.dev.controller.personas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.controller.personas.dto.ClienteSyncRequest;
import com.franco.dev.controller.personas.dto.PersonaSyncRequest;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.personas.PersonaGraphQL;
import com.franco.dev.graphql.personas.input.PersonaInput;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.dto.ImagenMetadataRequest;
import com.franco.dev.service.utils.ImageService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * /api/personas: el crédito sigue la misma regla que saveCliente (null conserva), un id inexistente es 404, y la
 * foto se sirve solo desde la carpeta de fotos de perfil (imagenes es un nombre de archivo, nunca una ruta).
 */
class PersonaSyncControllerTest {

    private PersonaSyncController c;
    private ClienteService clienteService;
    private PersonaService personaService;
    private FuncionarioService funcionarioService;
    private Persona persona;

    @TempDir
    Path imagenes;

    @BeforeEach
    void setUp() {
        c = new ObjenesisStd().newInstance(PersonaSyncController.class);
        clienteService = mock(ClienteService.class);
        personaService = mock(PersonaService.class);
        funcionarioService = mock(FuncionarioService.class);
        ImageService imageService = mock(ImageService.class);
        when(imageService.getImagePath()).thenReturn(imagenes.toString() + "/");
        ReflectionTestUtils.setField(c, "clienteService", clienteService);
        ReflectionTestUtils.setField(c, "personaService", personaService);
        ReflectionTestUtils.setField(c, "funcionarioService", funcionarioService);
        ReflectionTestUtils.setField(c, "imageService", imageService);
        persona = new Persona();
        persona.setId(7L);
        when(personaService.findById(7L)).thenReturn(Optional.of(persona));
        when(clienteService.save(any())).thenAnswer(i -> i.getArgument(0));
        when(personaService.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void sesion(String... roles) {
        ReflectionTestUtils.setField(c, "seg", new TesoreriaSecurityFalso(9L, roles));
    }

    private void guardado(Float credito) {
        Cliente g = new Cliente();
        g.setId(1L);
        g.setPersona(persona);
        g.setCredito(credito);
        when(clienteService.findById(1L)).thenReturn(Optional.of(g));
    }

    private static ClienteSyncRequest cliente(Long id, Float credito) {
        ClienteSyncRequest r = new ClienteSyncRequest();
        r.setId(id);
        r.setPersonaId(7L);
        r.setCredito(credito);
        return r;
    }

    private void rechazado(ClienteSyncRequest r) {
        assertThrows(GraphQLException.class, () -> c.saveCliente(r));
        verify(clienteService, never()).save(any());
    }

    @Test
    void altaSinCreditoPasaSinRol() {
        sesion();
        assertEquals(HttpStatus.CREATED, c.saveCliente(cliente(null, null)).getStatusCode());
        assertEquals(HttpStatus.CREATED, c.saveCliente(cliente(null, 0f)).getStatusCode());
    }

    @Test
    void altaConCreditoSinAdminSeRechaza() {
        sesion("VER USUARIOS");
        rechazado(cliente(null, 50000f));
    }

    @Test
    void edicionSinCreditoOIgualAlGuardadoPasa() {
        sesion();
        guardado(100000f);
        assertEquals(HttpStatus.OK, c.saveCliente(cliente(1L, null)).getStatusCode());
        assertEquals(HttpStatus.OK, c.saveCliente(cliente(1L, 100000f)).getStatusCode());
    }

    @Test
    void cambiarElCreditoSinAdminSeRechazaYConAdminPasa() {
        guardado(100000f);
        sesion("RRHH GESTIONAR");
        rechazado(cliente(1L, 900000f));
        sesion("ADMIN");
        assertEquals(900000f, c.saveCliente(cliente(1L, 900000f)).getBody().getCredito());
    }

    @Test
    void elCreditoDeUnFuncionarioLoCambiaRrhh() {
        guardado(0f);
        when(funcionarioService.findByPersonaId(7L)).thenReturn(new Funcionario());
        sesion("VER PERSONAS");
        rechazado(cliente(1L, 300000f));
        sesion("RRHH CONFIG");
        assertEquals(HttpStatus.OK, c.saveCliente(cliente(1L, 300000f)).getStatusCode());
    }

    @Test
    void pasarElClienteAOtraPersonaSinAdminSeRechaza() {
        sesion("RRHH GESTIONAR");
        guardado(0f);
        ClienteSyncRequest r = cliente(1L, null);
        r.setPersonaId(8L);
        rechazado(r);
    }

    @Test
    void unIdQueNoExisteEs404() {
        sesion("ADMIN");
        when(clienteService.findById(2L)).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> c.saveCliente(cliente(2L, null)));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        PersonaSyncRequest p = new PersonaSyncRequest();
        p.setId(99L);
        when(personaService.findById(99L)).thenReturn(Optional.empty());
        e = assertThrows(ResponseStatusException.class, () -> c.savePersona(p));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        verify(personaService, never()).save(any());
    }

    @Test
    void unaRutaEnImagenesSeRechaza() {
        sesion("ADMIN");
        PersonaSyncRequest p = new PersonaSyncRequest();
        p.setImagenes("/opt/frc-backend-central/bodega/.env");
        assertThrows(ResponseStatusException.class, () -> c.savePersona(p));
        ImagenMetadataRequest m = new ImagenMetadataRequest();
        m.setNombreImagen("../../etc/passwd");
        assertEquals(HttpStatus.BAD_REQUEST, c.updateImagenMetadata(7L, m).getStatusCode());
        verify(personaService, never()).save(any());
        verify(personaService, never()).saveLocal(any());
    }

    @Test
    void laFotoSeSirveSoloDesdeLaCarpetaDePerfil() throws Exception {
        Path perfil = Files.createDirectories(imagenes.resolve("personas").resolve("perfil"));
        Files.write(perfil.resolve("12_perfil1.png"), new byte[]{1, 2, 3});
        Path secreto = Files.write(imagenes.resolve("secreto.env"), new byte[]{9});

        persona.setImagenes("12_perfil1.png");
        assertEquals(HttpStatus.OK, c.getPersonaImage(7L).getStatusCode());

        persona.setImagenes(secreto.toString());
        assertEquals(HttpStatus.NOT_FOUND, c.getPersonaImage(7L).getStatusCode());
        persona.setImagenes("../../secreto.env");
        assertEquals(HttpStatus.NOT_FOUND, c.getPersonaImage(7L).getStatusCode());
    }

    @Test
    void laRespuestaNoLlevaLaClave() throws Exception {
        Usuario u = new Usuario();
        u.setId(5L);
        u.setPassword("hash-secreto");
        Cliente cl = new Cliente();
        cl.setUsuario(u);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(cl);
        assertFalse(json.contains("hash-secreto"), json);
    }

    @Test
    void savePersonaPorGraphQLTambienRechazaUnaRuta() {
        PersonaGraphQL r = new ObjenesisStd().newInstance(PersonaGraphQL.class);
        PersonaInput in = new PersonaInput();
        in.setImagenes("/opt/frc-backend-central/bodega/.env");
        assertThrows(GraphQLException.class, () -> r.savePersona(in));
    }

    @Test
    void unSymlinkDentroDeLaCarpetaQueApuntaAfueraNoSeSirve() throws Exception {
        Path perfil = Files.createDirectories(imagenes.resolve("personas").resolve("perfil"));
        Path secreto = Files.write(imagenes.resolve("secreto.env"), new byte[]{9});
        Files.createSymbolicLink(perfil.resolve("12_perfil2.png"), secreto);
        assertNull(c.resolverFotoPerfil("12_perfil2.png"));
        persona.setImagenes(",12_perfil2.png");
        assertEquals(HttpStatus.NOT_FOUND, c.getPersonaImage(7L).getStatusCode());
    }

    @Test
    void laPrimeraFotoNoVaciaSeSirveAunqueHayaUnaComaAdelante() throws Exception {
        Path perfil = Files.createDirectories(imagenes.resolve("personas").resolve("perfil"));
        Files.write(perfil.resolve("12_perfil3.png"), new byte[]{1});
        persona.setImagenes(" ,12_perfil3.png");
        assertEquals(HttpStatus.OK, c.getPersonaImage(7L).getStatusCode());
    }

    @Test
    void elAltaDeUnClienteFuncionarioConCreditoLaHaceRrhh() {
        when(funcionarioService.findByPersonaId(7L)).thenReturn(new Funcionario());
        sesion("VER PERSONAS");
        rechazado(cliente(null, 200000f));
        sesion("RRHH GESTIONAR");
        assertEquals(HttpStatus.CREATED, c.saveCliente(cliente(null, 200000f)).getStatusCode());
    }

    @Test
    void unRechazoDeRolSeRespondeConUn403ConElMotivo() {
        org.springframework.http.ResponseEntity<java.util.Map<String, String>> r =
                c.handleRechazo(new GraphQLException("No autorizado: esta acción es solo para un administrador."));
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertTrue(r.getBody().get("message").contains("administrador"));
    }

    // --- fase B: altas idempotentes y respuesta sin relaciones ---

    private Persona guardada(Long id, String documento) {
        Persona p = new Persona();
        p.setId(id);
        p.setDocumento(documento);
        p.setNombre("EXISTENTE");
        return p;
    }

    @Test
    void altaDePersonaConDvDevuelveLaExistenteSinTocarla() {
        sesion();
        when(personaService.findByDocumento("80018977")).thenReturn(guardada(50L, "80018977"));
        PersonaSyncRequest p = new PersonaSyncRequest();
        p.setDocumento(" 80018977-1 ");
        p.setNombre("OTRO NOMBRE");
        org.springframework.http.ResponseEntity<Persona> r = c.savePersona(p);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(50L, r.getBody().getId());
        assertEquals("EXISTENTE", r.getBody().getNombre());
        verify(personaService, never()).save(any());
    }

    @Test
    void altaDePersonaConXoSinDocumentoNoBuscaYCrea() {
        sesion();
        PersonaSyncRequest x = new PersonaSyncRequest();
        x.setDocumento("x");
        x.setNombre("SIN NOMBRE");
        assertEquals(HttpStatus.CREATED, c.savePersona(x).getStatusCode());
        PersonaSyncRequest sin = new PersonaSyncRequest();
        sin.setDocumento("   ");
        assertEquals(HttpStatus.CREATED, c.savePersona(sin).getStatusCode());
        verify(personaService, never()).findByDocumento(any());
    }

    @Test
    void altaDePersonaNuevaGuardaElDocumentoNormalizado() {
        sesion();
        PersonaSyncRequest p = new PersonaSyncRequest();
        p.setDocumento(" 7168390 ");
        c.savePersona(p);
        org.mockito.ArgumentCaptor<Persona> cap = org.mockito.ArgumentCaptor.forClass(Persona.class);
        verify(personaService).save(cap.capture());
        assertEquals("7168390", cap.getValue().getDocumento());
    }

    @Test
    void siOtraAltaGanaLaCarreraSeDevuelveLaExistente() {
        sesion();
        when(personaService.findByDocumento("4444441")).thenReturn(null, guardada(60L, "4444441"));
        when(personaService.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("persona_un_documento"));
        PersonaSyncRequest p = new PersonaSyncRequest();
        p.setDocumento("4444441");
        org.springframework.http.ResponseEntity<Persona> r = c.savePersona(p);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(60L, r.getBody().getId());
    }

    @Test
    void altaDeClienteDeUnaPersonaQueYaEsClienteDevuelveEseSinTocarlo() {
        sesion();
        Cliente existente = new Cliente();
        existente.setId(9L);
        existente.setPersona(persona);
        existente.setCredito(0f);
        when(clienteService.findByPersonaId(7L)).thenReturn(existente);
        org.springframework.http.ResponseEntity<Cliente> r = c.saveCliente(cliente(null, 900000f));
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(9L, r.getBody().getId());
        assertEquals(0f, r.getBody().getCredito());
        verify(clienteService, never()).save(any());
    }

    @Test
    void laRespuestaNoLlevaRelaciones() throws Exception {
        sesion("ADMIN");
        Usuario u = new Usuario();
        u.setId(5L);
        u.setPassword("hash-secreto");
        Cliente g = new Cliente();
        g.setId(1L);
        g.setPersona(persona);
        g.setUsuario(u);
        when(clienteService.findById(1L)).thenReturn(Optional.of(g));
        Cliente body = c.saveCliente(cliente(1L, null)).getBody();
        assertNull(body.getUsuario());
        assertNull(body.getPersona());
        persona.setUsuario(u);
        PersonaSyncRequest p = new PersonaSyncRequest();
        p.setId(7L);
        assertNull(c.savePersona(p).getBody().getUsuario());
    }

    @Test
    void dosAltasSimultaneasDeLaMismaPersonaGuardanUnSoloCliente() throws Exception {
        sesion();
        java.util.concurrent.atomic.AtomicReference<Cliente> enBase = new java.util.concurrent.atomic.AtomicReference<>();
        when(clienteService.findByPersonaId(7L)).thenAnswer(i -> enBase.get());
        when(clienteService.save(any())).thenAnswer(i -> {
            Thread.sleep(100);
            Cliente g = i.getArgument(0);
            g.setId(9L);
            enBase.set(g);
            return g;
        });
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<org.springframework.http.ResponseEntity<Cliente>> a = pool.submit(() -> c.saveCliente(cliente(null, null)));
            java.util.concurrent.Future<org.springframework.http.ResponseEntity<Cliente>> b = pool.submit(() -> c.saveCliente(cliente(null, null)));
            assertEquals(9L, a.get().getBody().getId());
            assertEquals(9L, b.get().getBody().getId());
        } finally {
            pool.shutdownNow();
        }
        verify(clienteService, times(1)).save(any());
    }
}
