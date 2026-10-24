package com.franco.dev.graphql.personas;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.personas.input.FuncionarioInput;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * saveFuncionario: sueldo, diarista y cargo tienen su flujo con historico en RRHH. Una edicion sin
 * RRHH GESTIONAR/CONFIG que los cambia se rechaza; si los reenvia iguales, pasa. Con el rol, se
 * cambian. El alta respeta lo que manda el wizard.
 */
class FuncionarioPagaRolTest {

    @Mock private FuncionarioService service;
    @Mock private UsuarioService usuarioService;
    @Mock private PersonaService personaService;
    @Mock private ClienteService clienteService;
    @Mock private CargoService cargoService;
    @InjectMocks private FuncionarioGraphQL resolver;

    private final Cargo cargoActual = new Cargo();
    private final Cargo cargoNuevo = new Cargo();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Persona persona = new Persona();
        persona.setId(7L);
        Funcionario existente = new Funcionario();
        existente.setId(1L);
        existente.setPersona(persona);
        existente.setActivo(true);
        existente.setSueldo(new BigDecimal("3000000"));
        existente.setDiarista(false);
        cargoActual.setId(10L);
        cargoNuevo.setId(20L);
        existente.setCargo(cargoActual);
        when(service.findById(1L)).thenReturn(Optional.of(existente));
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        when(personaService.findById(anyLong())).thenReturn(Optional.of(persona));
        when(clienteService.findByPersonaId(anyLong())).thenReturn(new Cliente());
        when(usuarioService.findByPersonaId(anyLong())).thenReturn(new Usuario());
        when(cargoService.findById(20L)).thenReturn(Optional.of(cargoNuevo));
    }

    private void sesion(String... roles) {
        ReflectionTestUtils.setField(resolver, "seg", new TesoreriaSecurityFalso(9L, roles));
    }

    private static FuncionarioInput edicion() {
        FuncionarioInput in = new FuncionarioInput();
        in.setId(1L);
        in.setActivo(true);
        in.setSueldo(9000000f);
        in.setDiarista(true);
        in.setCargoId(20L);
        return in;
    }

    private static FuncionarioInput reenvioSinCambios() {
        FuncionarioInput in = new FuncionarioInput();
        in.setId(1L);
        in.setActivo(true);
        in.setSueldo(3000000f);
        in.setDiarista(false);
        in.setCargoId(10L);
        return in;
    }

    @Test
    void sinRolDeRrhhReenviarLosMismosValoresPasaSinCambiarlos() {
        sesion("VER FUNCIONARIOS");
        Funcionario f = resolver.saveFuncionario(reenvioSinCambios());
        assertEquals(0, new BigDecimal("3000000").compareTo(f.getSueldo()));
        assertEquals(Boolean.FALSE, f.getDiarista());
        assertSame(cargoActual, f.getCargo());
    }

    @Test
    void sinRolDeRrhhCambiarDiaristaSeRechaza() {
        sesion("RRHH VER");
        FuncionarioInput in = reenvioSinCambios();
        in.setDiarista(true);
        assertThrows(GraphQLException.class, () -> resolver.saveFuncionario(in));
    }

    @Test
    void sinRolDeRrhhCambiarSueldoOCargoSeRechaza() {
        sesion("VER FUNCIONARIOS");
        FuncionarioInput sueldo = reenvioSinCambios();
        sueldo.setSueldo(9000000f);
        assertThrows(GraphQLException.class, () -> resolver.saveFuncionario(sueldo));
        FuncionarioInput cargo = reenvioSinCambios();
        cargo.setCargoId(20L);
        assertThrows(GraphQLException.class, () -> resolver.saveFuncionario(cargo));
    }

    @Test
    void conRrhhGestionarLosCambia() {
        sesion("RRHH GESTIONAR");
        Funcionario f = resolver.saveFuncionario(edicion());
        assertEquals(0, new BigDecimal("9000000").compareTo(f.getSueldo()));
        assertEquals(Boolean.TRUE, f.getDiarista());
        assertSame(cargoNuevo, f.getCargo());
    }

    @Test
    void conRrhhConfigCambiaElSueldoPeroNoElCargo() {
        sesion("RRHH CONFIG", "VER FUNCIONARIOS");
        FuncionarioInput in = edicion();
        in.setCargoId(10L);
        Funcionario f = resolver.saveFuncionario(in);
        assertEquals(0, new BigDecimal("9000000").compareTo(f.getSueldo()));
        assertSame(cargoActual, f.getCargo());
        assertThrows(GraphQLException.class, () -> resolver.saveFuncionario(edicion()));
    }

    @Test
    void elAltaRespetaElSueldoDelWizard() {
        sesion("VER FUNCIONARIOS");
        FuncionarioInput alta = new FuncionarioInput();
        alta.setPersonaId(7L);
        alta.setSueldo(2500000f);
        Funcionario f = resolver.saveFuncionario(alta);
        assertEquals(0, new BigDecimal("2500000").compareTo(f.getSueldo()));
    }

    @Test
    void sinNingunRolDeFuncionariosSeRechaza() {
        sesion("VENTA TOUCH");
        assertThrows(GraphQLException.class, () -> resolver.saveFuncionario(edicion()));
    }
}
