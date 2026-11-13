package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.financiero.dto.ConvenioRemoto;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConvenioRemotoServiceTest {

    private static final String URL = "http://remoto/graphql";

    private final UsuarioService usuarios = mock(UsuarioService.class);
    private RestTemplate rest;
    private MockRestServiceServer remoto;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
        remoto = MockRestServiceServer.bindTo(rest).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("JUAN", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ConvenioRemotoService service(String url, String token) {
        return new ConvenioRemotoService(usuarios, new ObjectMapper(), rest, url, token, "Bodega");
    }

    private void personaEnSesion(String documento) {
        Persona p = new Persona();
        p.setDocumento(documento);
        Usuario u = new Usuario();
        u.setPersona(p);
        when(usuarios.findByNickname("JUAN")).thenReturn(Optional.of(u));
    }

    private void responde(String bodyContiene, String json) {
        remoto.expect(requestTo(URL))
                .andExpect(header("Authorization", "Token tk"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(bodyContiene)))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void sinConfigurarNoLlamaAlRemoto() {
        personaEnSesion("1234567");
        remoto.expect(never(), requestTo(URL));
        assertNull(service("", "tk").deLaPersonaEnSesion());
        assertNull(service(URL, " ").deLaPersonaEnSesion());
        remoto.verify();
    }

    @Test
    void sinDocumentoDevuelveNull() {
        personaEnSesion("  ");
        remoto.expect(never(), requestTo(URL));
        assertNull(service(URL, "tk").deLaPersonaEnSesion());
        remoto.verify();
    }

    @Test
    void sumaLosAbiertosYMandaElDocumentoExacto() {
        personaEnSesion(" 1.234.567-8 ");
        responde("\"texto\":\"1.234.567-8\"", "{\"data\":{\"data\":{\"id\":\"40\"}}}");
        // El id de la persona va a clientePorPersonaId y el del cliente a ventaCreditoPorCliente: cruzarlos
        // mezclaría créditos de personas distintas.
        responde("\"variables\":{\"id\":\"40\"}", "{\"data\":{\"data\":{\"id\":\"7\",\"credito\":500000.0}}}");
        responde("\"variables\":{\"id\":\"7\"}", "{\"data\":{\"data\":[{\"valorTotal\":100000},{\"valorTotal\":25000.5},{\"valorTotal\":null}]}}");

        ConvenioRemoto r = service(URL, "tk").deLaPersonaEnSesion();

        assertEquals("Bodega", r.getEmpresa());
        assertEquals(500000.0, r.getLimite());
        assertEquals(125000.5, r.getUtilizado());
        remoto.verify();
    }

    @Test
    void personaQueNoExisteAllaDevuelveNull() {
        personaEnSesion("1234567");
        responde("personaPorDocumento", "{\"data\":{\"data\":null}}");
        assertNull(service(URL, "tk").deLaPersonaEnSesion());
        remoto.verify();
    }

    @Test
    void personaQueNoEsClienteAllaDevuelveNull() {
        personaEnSesion("1234567");
        responde("personaPorDocumento", "{\"data\":{\"data\":{\"id\":\"40\"}}}");
        responde("clientePorPersonaId", "{\"data\":{\"data\":null}}");
        assertNull(service(URL, "tk").deLaPersonaEnSesion());
        remoto.verify();
    }

    @Test
    void erroresDelRemotoLanzan() {
        // p. ej. dos clientes para la misma persona: findByPersonaId lanza allá.
        personaEnSesion("1234567");
        responde("personaPorDocumento", "{\"data\":{\"data\":{\"id\":\"40\"}}}");
        responde("clientePorPersonaId", "{\"errors\":[{\"message\":\"query did not return a unique result\"}],\"data\":{\"data\":null}}");
        GraphQLException e = assertThrows(GraphQLException.class, () -> service(URL, "tk").deLaPersonaEnSesion());
        assertEquals("No se pudo consultar el crédito en Bodega", e.getMessage());
    }

    @Test
    void respuestaConOtraFormaLanzaNoSePudo() {
        personaEnSesion("1234567");
        responde("personaPorDocumento", "{\"data\":{\"data\":[{\"id\":\"40\"}]}}");
        GraphQLException e = assertThrows(GraphQLException.class, () -> service(URL, "tk").deLaPersonaEnSesion());
        assertEquals("No se pudo consultar el crédito en Bodega", e.getMessage());
    }

    @Test
    void idsNumericosTambienSirven() {
        personaEnSesion("1234567");
        responde("personaPorDocumento", "{\"data\":{\"data\":{\"id\":40}}}");
        responde("\"variables\":{\"id\":40}", "{\"data\":{\"data\":{\"id\":7,\"credito\":null}}}");
        responde("\"variables\":{\"id\":7}", "{\"data\":{\"data\":[]}}");
        ConvenioRemoto r = service(URL, "tk").deLaPersonaEnSesion();
        assertEquals(0.0, r.getLimite());
        assertEquals(0.0, r.getUtilizado());
        remoto.verify();
    }

    @Test
    void errorHttpLanza() {
        personaEnSesion("1234567");
        remoto.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThrows(GraphQLException.class, () -> service(URL, "tk").deLaPersonaEnSesion());
    }

    @Test
    void sinSesionDevuelveNull() {
        SecurityContextHolder.clearContext();
        remoto.expect(never(), requestTo(URL));
        assertNull(service(URL, "tk").deLaPersonaEnSesion());
        remoto.verify();
    }
}
