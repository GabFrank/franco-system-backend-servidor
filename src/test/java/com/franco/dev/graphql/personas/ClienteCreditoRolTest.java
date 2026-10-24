package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.PersonaService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * saveCliente: cambiar el credito exige ADMIN, o RRHH GESTIONAR / CONFIG si la persona es funcionario (el save
 * se lo copia al legajo). Un credito igual al guardado (null = 0) o que no viene pasa sin rol, y el control va
 * antes de guardar la persona o el cliente.
 */
class ClienteCreditoRolTest {

    private ClienteGraphQL resolver;
    private ClienteService service;
    private PersonaService personaService;
    private FuncionarioService funcionarioService;
    private Persona persona;

    @BeforeEach
    void setUp() {
        resolver = new ObjenesisStd().newInstance(ClienteGraphQL.class);
        service = mock(ClienteService.class);
        personaService = mock(PersonaService.class);
        funcionarioService = mock(FuncionarioService.class);
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "personaService", personaService);
        ReflectionTestUtils.setField(resolver, "funcionarioService", funcionarioService);
        persona = new Persona();
        persona.setId(7L);
        when(personaService.findById(7L)).thenReturn(Optional.of(persona));
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void sesion(String... roles) {
        ReflectionTestUtils.setField(resolver, "seg", new TesoreriaSecurityFalso(9L, roles));
    }

    private void guardado(Float credito) {
        Cliente c = new Cliente();
        c.setId(1L);
        c.setPersona(persona);
        c.setCredito(credito);
        when(service.findById(1L)).thenReturn(Optional.of(c));
    }

    private Funcionario esFuncionario(Float credito) {
        Funcionario f = new Funcionario();
        f.setCredito(credito);
        when(funcionarioService.findByPersonaId(7L)).thenReturn(f);
        return f;
    }

    private static ClienteInput edicion(Float credito) {
        ClienteInput in = new ClienteInput();
        in.setId(1L);
        in.setPersonaId(7L);
        in.setCredito(credito);
        return in;
    }

    private void rechazado(ClienteInput in) {
        assertThrows(GraphQLException.class, () -> resolver.saveCliente(in));
        verify(service, never()).save(any());
        verify(personaService, never()).save(any());
        verify(funcionarioService, never()).save(any());
    }

    @Test
    void conVerPersonasEditarSinTocarElCreditoPasa() {
        sesion("VER PERSONAS");
        guardado(null);
        assertNotNull(resolver.saveCliente(edicion(0f)));
    }

    @Test
    void unCreditoQueNoVieneConservaElGuardado() {
        sesion("VER USUARIOS");
        guardado(500000f);
        assertEquals(500000f, resolver.saveCliente(edicion(null)).getCredito());
    }

    @Test
    void cambiarElCreditoDeUnClienteSinAdminSeRechaza() {
        sesion("VER USUARIOS", "RRHH GESTIONAR");
        guardado(100000f);
        rechazado(edicion(900000f));
    }

    @Test
    void adminCambiaElCreditoDeUnCliente() {
        sesion("ADMIN");
        guardado(100000f);
        assertEquals(900000f, resolver.saveCliente(edicion(900000f)).getCredito());
    }

    @Test
    void elCreditoDeUnFuncionarioLoCambiaRrhhYSeCopiaAlLegajo() {
        guardado(100000f);
        Funcionario f = esFuncionario(100000f);
        sesion("VER PERSONAS");
        rechazado(edicion(900000f));
        sesion("VER PERSONAS", "RRHH CONFIG");
        resolver.saveCliente(edicion(900000f));
        assertEquals(900000f, f.getCredito());
        verify(funcionarioService).save(f);
    }

    @Test
    void sinCambioDeCreditoNoSeTocaElLegajo() {
        sesion("VER PERSONAS");
        guardado(100000f);
        esFuncionario(100000f);
        resolver.saveCliente(edicion(100000f));
        verify(funcionarioService, never()).save(any());
    }

    @Test
    void elAltaConCreditoSinAdminSeRechazaYSinCreditoPasa() {
        sesion("VER USUARIOS");
        ClienteInput alta = new ClienteInput();
        alta.setPersonaId(7L);
        alta.setCredito(50000f);
        rechazado(alta);
        alta.setCredito(0f);
        assertNotNull(resolver.saveCliente(alta));
    }

    @Test
    void elAltaParaUnFuncionarioNoLePisaElCredito() {
        sesion("VER USUARIOS");
        esFuncionario(100000f);
        ClienteInput alta = new ClienteInput();
        alta.setPersonaId(7L);
        alta.setCredito(0f);
        rechazado(alta);
        alta.setCredito(100000f);
        assertNotNull(resolver.saveCliente(alta));
        verify(funcionarioService, never()).save(any());
    }

    @Test
    void pasarElClienteAOtraPersonaSinAdminSeRechaza() {
        sesion("VER PERSONAS", "RRHH GESTIONAR");
        guardado(100000f);
        Persona otra = new Persona();
        otra.setId(8L);
        when(personaService.findById(8L)).thenReturn(Optional.of(otra));
        Funcionario ajeno = new Funcionario();
        ajeno.setCredito(0f);
        when(funcionarioService.findByPersonaId(8L)).thenReturn(ajeno);
        ClienteInput in = edicion(null);
        in.setPersonaId(8L);
        rechazado(in);
        sesion("ADMIN");
        resolver.saveCliente(in);
        assertEquals(100000f, ajeno.getCredito());
    }

    @Test
    void editarUnClienteQueNoExisteSeRechaza() {
        sesion("VER PERSONAS");
        when(service.findById(2L)).thenReturn(Optional.empty());
        ClienteInput in = edicion(0f);
        in.setId(2L);
        rechazado(in);
    }

    @Test
    void editarConservaElSaldoDeLaCuentaCorriente() {
        sesion("VER PERSONAS");
        Cliente c = new Cliente();
        c.setId(1L);
        c.setPersona(persona);
        c.setSaldoActual(new java.math.BigDecimal("250000"));
        when(service.findById(1L)).thenReturn(Optional.of(c));
        assertEquals(0, new java.math.BigDecimal("250000").compareTo(resolver.saveCliente(edicion(null)).getSaldoActual()));
    }
}
