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
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * saveFuncionario. Alta: CREAR FUNCIONARIOS, VER FUNCIONARIOS o RRHH GESTIONAR. Edicion: EDITAR FUNCIONARIOS o
 * RRHH GESTIONAR. En la edicion, sueldo, diarista, credito, banco e IPS los cambia RRHH GESTIONAR o CONFIG, y
 * cargo y activo solo GESTIONAR. Sin el rol, reenviar lo guardado pasa (el legajo manda todo) y cambiarlo se
 * rechaza sin guardar.
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
        existente.setCredito(null); // el legajo lo manda como 0
        existente.setCobraBanco(true);
        existente.setCuentaBancaria("123-ABC");
        existente.setIpsActivo(true);
        existente.setNumeroIps("IPS9");
        existente.setFechaIngresoIps(LocalDate.of(2024, 3, 1));
        cargoActual.setId(10L);
        cargoNuevo.setId(20L);
        existente.setCargo(cargoActual);
        when(service.findById(1L)).thenAnswer(i -> Optional.of(copia(existente)));
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        when(personaService.findById(anyLong())).thenReturn(Optional.of(persona));
        when(clienteService.findByPersonaId(anyLong())).thenReturn(new Cliente());
        when(usuarioService.findByPersonaId(anyLong())).thenReturn(new Usuario());
        when(cargoService.findById(20L)).thenReturn(Optional.of(cargoNuevo));
    }

    /** Cada findById devuelve una entidad nueva, como Hibernate entre llamadas distintas. */
    private static Funcionario copia(Funcionario o) {
        Funcionario f = new Funcionario();
        f.setId(o.getId()); f.setPersona(o.getPersona()); f.setActivo(o.getActivo()); f.setSueldo(o.getSueldo());
        f.setDiarista(o.getDiarista()); f.setCredito(o.getCredito()); f.setCobraBanco(o.getCobraBanco());
        f.setCuentaBancaria(o.getCuentaBancaria()); f.setIpsActivo(o.getIpsActivo()); f.setNumeroIps(o.getNumeroIps());
        f.setFechaIngresoIps(o.getFechaIngresoIps()); f.setCargo(o.getCargo());
        return f;
    }

    private void sesion(String... roles) {
        ReflectionTestUtils.setField(resolver, "seg", new TesoreriaSecurityFalso(9L, roles));
    }

    /** Lo que manda el legajo al guardar sin tocar nada: todo, normalizado, credito vacio como 0. */
    private static FuncionarioInput legajoSinCambios() {
        FuncionarioInput in = new FuncionarioInput();
        in.setId(1L);
        in.setActivo(true);
        in.setDiarista(false);
        in.setCredito(0f);
        in.setCobraBanco(true);
        in.setCuentaBancaria("123-abc ");
        in.setIpsActivo(true);
        in.setNumeroIps(" ips9");
        in.setFechaIngresoIps("2024-03-01");
        return in;
    }

    private static FuncionarioInput legajoCon(Consumer<FuncionarioInput> cambio) {
        FuncionarioInput in = legajoSinCambios();
        cambio.accept(in);
        return in;
    }

    private void rechazado(FuncionarioInput in) {
        assertThrows(GraphQLException.class, () -> resolver.saveFuncionario(in));
        verify(service, never()).save(any());
    }

    @Test
    void sinRolDeRrhhElLegajoSinCambiosPasaYConservaLoGuardado() {
        sesion("EDITAR FUNCIONARIOS");
        Funcionario f = resolver.saveFuncionario(legajoSinCambios());
        assertEquals(0, new BigDecimal("3000000").compareTo(f.getSueldo()));
        assertEquals(Boolean.FALSE, f.getDiarista());
        assertNull(f.getCredito());
        assertEquals("123-ABC", f.getCuentaBancaria());
        assertEquals("IPS9", f.getNumeroIps());
        assertEquals(LocalDate.of(2024, 3, 1), f.getFechaIngresoIps());
        assertSame(cargoActual, f.getCargo());
        assertEquals(Boolean.TRUE, f.getActivo());
    }

    @Test
    void laAsignacionDeHorarioConLosMismosValoresPasa() {
        sesion("EDITAR FUNCIONARIOS");
        FuncionarioInput in = new FuncionarioInput();
        in.setId(1L);
        in.setSueldo(3000000f);
        in.setCargoId(10L);
        in.setActivo(true);
        assertNotNull(resolver.saveFuncionario(in));
    }

    @Test
    void sinRolDeRrhhCambiarUnCampoDePagaSeRechaza() {
        sesion("EDITAR FUNCIONARIOS");
        rechazado(legajoCon(in -> in.setDiarista(true)));
        rechazado(legajoCon(in -> in.setCredito(500000f)));
        rechazado(legajoCon(in -> in.setCuentaBancaria("999")));
        rechazado(legajoCon(in -> { in.setCobraBanco(false); in.setCuentaBancaria(null); }));
        rechazado(legajoCon(in -> in.setNumeroIps("OTRO")));
        rechazado(legajoCon(in -> in.setFechaIngresoIps("2024-03-02")));
        rechazado(legajoCon(in -> { in.setIpsActivo(false); in.setNumeroIps(null); in.setFechaIngresoIps(null); }));
        rechazado(legajoCon(in -> in.setSueldo(9000000f)));
    }

    @Test
    void sinRrhhGestionarCambiarCargoOActivoSeRechaza() {
        sesion("EDITAR FUNCIONARIOS", "RRHH CONFIG");
        rechazado(legajoCon(in -> in.setCargoId(20L)));
        rechazado(legajoCon(in -> in.setActivo(false)));
    }

    @Test
    void conRrhhConfigCambiaPagaYBancoPeroNoCargo() {
        sesion("EDITAR FUNCIONARIOS", "RRHH CONFIG");
        Funcionario f = resolver.saveFuncionario(legajoCon(in -> {
            in.setSueldo(9000000f);
            in.setCredito(500000f);
            in.setCuentaBancaria("999");
        }));
        assertEquals(0, new BigDecimal("9000000").compareTo(f.getSueldo()));
        assertEquals(500000f, f.getCredito());
        assertEquals("999", f.getCuentaBancaria());
        assertSame(cargoActual, f.getCargo());
    }

    @Test
    void conRrhhGestionarCambiaTodo() {
        sesion("RRHH GESTIONAR");
        Funcionario f = resolver.saveFuncionario(legajoCon(in -> {
            in.setDiarista(true);
            in.setCargoId(20L);
            in.setActivo(false);
        }));
        assertEquals(Boolean.TRUE, f.getDiarista());
        assertSame(cargoNuevo, f.getCargo());
        assertEquals(Boolean.FALSE, f.getActivo());
    }

    @Test
    void laEdicionConRolesDeLecturaOSoloDeAltaSeRechaza() {
        for (String rol : new String[]{"VER FUNCIONARIOS", "CREAR FUNCIONARIOS", "VER PERSONAS", "RRHH VER", "RRHH CONFIG"}) {
            sesion(rol);
            assertThrows(GraphQLException.class, () -> resolver.saveFuncionario(legajoSinCambios()), rol);
        }
        verify(service, never()).save(any());
    }

    @Test
    void elAltaPasaConVerFuncionariosYRespetaElWizard() {
        sesion("VER FUNCIONARIOS");
        FuncionarioInput alta = new FuncionarioInput();
        alta.setPersonaId(7L);
        alta.setSueldo(2500000f);
        Funcionario f = resolver.saveFuncionario(alta);
        assertEquals(0, new BigDecimal("2500000").compareTo(f.getSueldo()));
    }

    @Test
    void elAltaConRolesDeLecturaSeRechaza() {
        sesion("VER PERSONAS", "RRHH VER", "EDITAR FUNCIONARIOS");
        FuncionarioInput alta = new FuncionarioInput();
        alta.setPersonaId(7L);
        assertThrows(GraphQLException.class, () -> resolver.saveFuncionario(alta));
    }

    @Test
    void unIdInexistenteSeComparaContraLosValoresPorDefecto() {
        sesion("EDITAR FUNCIONARIOS");
        when(service.findById(2L)).thenReturn(Optional.empty());
        FuncionarioInput in = new FuncionarioInput();
        in.setId(2L);
        in.setPersonaId(7L);
        in.setActivo(true);
        in.setCredito(0f);
        in.setCobraBanco(false);
        in.setIpsActivo(false);
        assertNotNull(resolver.saveFuncionario(in));
        FuncionarioInput conCredito = new FuncionarioInput();
        conCredito.setId(2L);
        conCredito.setCredito(1f);
        assertThrows(GraphQLException.class, () -> resolver.saveFuncionario(conCredito));
    }
}
