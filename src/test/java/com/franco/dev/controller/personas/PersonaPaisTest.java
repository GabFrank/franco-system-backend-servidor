package com.franco.dev.controller.personas;

import com.franco.dev.controller.personas.dto.PersonaSyncRequest;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.personas.ClienteGraphQL;
import com.franco.dev.graphql.personas.PersonaGraphQL;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.graphql.personas.input.PersonaInput;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.PersonaService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Pais del receptor en el central (#666): la caja lo pone solo en el alta o si la persona no tiene (endpoint);
 * corregirlo es del admin (savePersona / saveCliente). Un pasaporte con guion no se corta como un DV.
 */
class PersonaPaisTest {

    private PersonaSyncController c;
    private PersonaService personaService;
    private PaisService paisService;
    private Persona persona;
    private final Pais brasil = pais(30L, "BRA");

    private static Pais pais(Long id, String codigo) {
        Pais p = new Pais();
        p.setId(id);
        p.setCodigo(codigo);
        return p;
    }

    @BeforeEach
    void setUp() {
        c = new ObjenesisStd().newInstance(PersonaSyncController.class);
        personaService = mock(PersonaService.class);
        paisService = mock(PaisService.class);
        ReflectionTestUtils.setField(c, "personaService", personaService);
        ReflectionTestUtils.setField(c, "paisService", paisService);
        ReflectionTestUtils.setField(c, "seg", new TesoreriaSecurityFalso(9L, "VENTA TOUCH"));
        persona = new Persona();
        persona.setId(7L);
        when(personaService.findById(7L)).thenReturn(Optional.of(persona));
        when(personaService.save(any())).thenAnswer(i -> i.getArgument(0));
        when(paisService.findById(30L)).thenReturn(Optional.of(brasil));
        when(paisService.findById(999L)).thenReturn(Optional.empty());
    }

    private Persona guardada() {
        ArgumentCaptor<Persona> cap = ArgumentCaptor.forClass(Persona.class);
        verify(personaService).save(cap.capture());
        return cap.getValue();
    }

    @Test
    void laEdicionPorIdNoTocaElPais() {
        Pais argentina = pais(10L, "ARG");
        persona.setPais(argentina);
        PersonaSyncRequest r = new PersonaSyncRequest();
        r.setId(7L);
        c.savePersona(r);
        assertSame(argentina, guardada().getPais());
    }

    @Test
    void elEndpointAsignaSoloSiEsNulo() {
        when(personaService.asignarPaisSiNulo(7L, 30L)).thenReturn(true);
        assertEquals(true, c.asignarPais(7L, Collections.singletonMap("paisId", 30L)).getBody().get("asignado"));
        verify(personaService).asignarPaisSiNulo(7L, 30L);
        verify(personaService, never()).save(any());
    }

    @Test
    void elEndpointRechazaPaisInexistenteYPersonaInexistente() {
        ResponseStatusException sinPais = assertThrows(ResponseStatusException.class,
                () -> c.asignarPais(7L, Collections.singletonMap("paisId", 999L)));
        assertEquals(HttpStatus.BAD_REQUEST, sinPais.getStatus());
        assertThrows(ResponseStatusException.class, () -> c.asignarPais(7L, Collections.emptyMap()));
        when(personaService.findById(8L)).thenReturn(Optional.empty());
        ResponseStatusException sinPersona = assertThrows(ResponseStatusException.class,
                () -> c.asignarPais(8L, Collections.singletonMap("paisId", 30L)));
        assertEquals(HttpStatus.NOT_FOUND, sinPersona.getStatus());
        verify(personaService, never()).asignarPaisSiNulo(anyLong(), anyLong());
    }

    @Test
    void elEndpointSinRolSeRechazaAntesDeTocarNada() {
        ReflectionTestUtils.setField(c, "seg", new TesoreriaSecurityFalso(9L, "VER CAJA"));
        assertThrows(GraphQLException.class, () -> c.asignarPais(7L, Collections.singletonMap("paisId", 30L)));
        verify(personaService, never()).asignarPaisSiNulo(anyLong(), anyLong());
        // con un rol de administracion de personas tambien pasa
        ReflectionTestUtils.setField(c, "seg", new TesoreriaSecurityFalso(9L, "VER PERSONAS"));
        when(personaService.asignarPaisSiNulo(7L, 30L)).thenReturn(true);
        assertEquals(true, c.asignarPais(7L, Collections.singletonMap("paisId", 30L)).getBody().get("asignado"));
    }

    @Test
    void unPasaporteConGuionNoSeCortaYUnRucSi() {
        assertEquals("AB-12345", PersonaService.sinDv("AB-12345"));
        assertEquals("FX987654", PersonaService.sinDv("fx987654"));
        assertEquals("AB-12345", PersonaSyncController.normalizarDocumento(" AB-12345 "));
        assertEquals("4043581", PersonaService.sinDv("4043581-2"));
        assertEquals("4043581", PersonaSyncController.normalizarDocumento("4043581-2"));
        assertNull(PersonaService.sinDv(null));
    }

    @Test
    void savePersonaCorrigeElPaisYRechazaUnoInexistente() {
        PersonaGraphQL r = new ObjenesisStd().newInstance(PersonaGraphQL.class);
        ReflectionTestUtils.setField(r, "seg", new TesoreriaSecurityFalso(1L, "VER PERSONAS"));
        ReflectionTestUtils.setField(r, "service", personaService);
        ReflectionTestUtils.setField(r, "paisService", paisService);
        persona.setPais(pais(10L, "ARG"));
        PersonaInput in = new PersonaInput();
        in.setId(7L);
        in.setPaisId(30L);
        r.savePersona(in);
        assertSame(brasil, guardada().getPais());

        in.setPaisId(999L);
        assertThrows(GraphQLException.class, () -> r.savePersona(in));
    }

    @Test
    void saveClienteCorrigeElPaisDeLaPersona() {
        ClienteGraphQL r = new ObjenesisStd().newInstance(ClienteGraphQL.class);
        ClienteService clienteService = mock(ClienteService.class);
        when(clienteService.save(any())).thenAnswer(i -> i.getArgument(0));
        Cliente previo = new Cliente();
        previo.setId(1L);
        previo.setPersona(persona);
        when(clienteService.findById(1L)).thenReturn(Optional.of(previo));
        ReflectionTestUtils.setField(r, "seg", new TesoreriaSecurityFalso(9L, "VER PERSONAS"));
        ReflectionTestUtils.setField(r, "service", clienteService);
        ReflectionTestUtils.setField(r, "personaService", personaService);
        ReflectionTestUtils.setField(r, "funcionarioService", mock(FuncionarioService.class));
        ReflectionTestUtils.setField(r, "paisService", paisService);
        ClienteInput in = new ClienteInput();
        in.setId(1L);
        in.setPersonaId(7L);
        in.setPaisId(30L);
        Cliente guardado = r.saveCliente(in);
        assertSame(brasil, guardado.getPersona().getPais());
        assertSame(brasil, guardada().getPais());
    }

    /** El ModelMapper de saveCliente no confunde paisId con la persona: alta sin personaId. */
    @Test
    void saveClienteNuevoLleveElPaisALaPersonaNueva() {
        ClienteGraphQL r = new ObjenesisStd().newInstance(ClienteGraphQL.class);
        ClienteService clienteService = mock(ClienteService.class);
        when(clienteService.save(any())).thenAnswer(i -> i.getArgument(0));
        ReflectionTestUtils.setField(r, "seg", new TesoreriaSecurityFalso(9L, "VER PERSONAS"));
        ReflectionTestUtils.setField(r, "service", clienteService);
        ReflectionTestUtils.setField(r, "personaService", personaService);
        ReflectionTestUtils.setField(r, "funcionarioService", mock(FuncionarioService.class));
        ReflectionTestUtils.setField(r, "paisService", paisService);
        ClienteInput in = new ClienteInput();
        in.setNombre("JOAO");
        in.setDocumento("FX1");
        in.setPaisId(30L);
        Cliente guardado = r.saveCliente(in);
        assertSame(brasil, guardado.getPersona().getPais());
        assertNull(guardado.getPersona().getId());
    }
}
