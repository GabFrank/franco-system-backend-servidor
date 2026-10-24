package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.personas.input.FuncionarioInput;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * saveFuncionario: un guardado que no gestiona los datos del legajo (IPS, banco, codigo interno,
 * contacto, creado_en) no los borra, y un alta sin IPS/banco queda en false, no en null (#452).
 * El ModelMapper es el real: lo crea el metodo.
 */
class FuncionarioGraphQLDatosLegajoTest {

    private static final LocalDate FECHA_IPS = LocalDate.of(2025, 3, 1);
    private static final LocalDateTime CREADO_EN = LocalDateTime.of(2025, 1, 10, 8, 0);

    @Mock private FuncionarioService service;
    @Mock private UsuarioService usuarioService;
    @Mock private PersonaService personaService;
    @Mock private ClienteService clienteService;
    @Mock private com.franco.dev.service.financiero.TesoreriaSecurityService seg;
    @InjectMocks private FuncionarioGraphQL resolver;

    private Funcionario existente;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Persona persona = new Persona();
        persona.setId(7L);
        persona.setNombre("JUAN PEREZ");

        existente = new Funcionario();
        existente.setId(1L);
        existente.setPersona(persona);
        existente.setActivo(true);
        existente.setIpsActivo(true);
        existente.setNumeroIps("IPS-123");
        existente.setFechaIngresoIps(FECHA_IPS);
        existente.setCobraBanco(true);
        existente.setCuentaBancaria("CTA-9");
        existente.setCodigoInterno("C-01");
        existente.setContactoEmergenciaNombre("MARIA");
        existente.setContactoEmergenciaTelefono("0981");
        existente.setCreadoEn(CREADO_EN);

        when(service.findById(1L)).thenReturn(Optional.of(existente));
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        when(personaService.findById(anyLong())).thenReturn(Optional.of(persona));
        when(clienteService.findByPersonaId(anyLong())).thenReturn(new Cliente());
        when(usuarioService.findByPersonaId(anyLong())).thenReturn(new Usuario());
    }

    /** Lo que manda la asignacion de horario en lote: ningun dato del legajo. */
    private FuncionarioInput updateSinLegajo() {
        FuncionarioInput in = new FuncionarioInput();
        in.setId(1L);
        in.setHorarioId(null);
        in.setActivo(true);
        return in;
    }

    private void assertBloqueIpsIntacto(Funcionario f) {
        assertEquals(Boolean.TRUE, f.getIpsActivo());
        assertEquals("IPS-123", f.getNumeroIps());
        assertEquals(FECHA_IPS, f.getFechaIngresoIps());
    }

    private void assertBloqueBancoIntacto(Funcionario f) {
        assertEquals(Boolean.TRUE, f.getCobraBanco());
        assertEquals("CTA-9", f.getCuentaBancaria());
    }

    @Test
    void update_sin_datos_del_legajo_los_conserva() {
        Funcionario f = resolver.saveFuncionario(updateSinLegajo());
        assertBloqueIpsIntacto(f);
        assertBloqueBancoIntacto(f);
        assertEquals("C-01", f.getCodigoInterno());
        assertEquals("MARIA", f.getContactoEmergenciaNombre());
        assertEquals("0981", f.getContactoEmergenciaTelefono());
    }

    @Test
    void update_sin_creado_en_lo_conserva() {
        assertEquals(CREADO_EN, resolver.saveFuncionario(updateSinLegajo()).getCreadoEn());
    }

    @Test
    void legajo_que_apaga_ips_y_banco_los_borra() {
        FuncionarioInput in = updateSinLegajo();
        in.setIpsActivo(false);
        in.setCobraBanco(false);
        Funcionario f = resolver.saveFuncionario(in);
        assertEquals(Boolean.FALSE, f.getIpsActivo());
        assertNull(f.getNumeroIps());
        assertNull(f.getFechaIngresoIps());
        assertEquals(Boolean.FALSE, f.getCobraBanco());
        assertNull(f.getCuentaBancaria());
    }

    @Test
    void legajo_que_prende_ips_guarda_los_datos_nuevos() {
        FuncionarioInput in = updateSinLegajo();
        in.setIpsActivo(true);
        in.setNumeroIps("IPS-999");
        in.setFechaIngresoIps("2026-02-01");
        Funcionario f = resolver.saveFuncionario(in);
        assertEquals(Boolean.TRUE, f.getIpsActivo());
        assertEquals("IPS-999", f.getNumeroIps());
        assertEquals(LocalDate.of(2026, 2, 1), f.getFechaIngresoIps());
    }

    @Test
    void alta_sin_ips_ni_banco_queda_en_false() {
        FuncionarioInput in = new FuncionarioInput();
        in.setPersonaId(7L);
        Funcionario f = resolver.saveFuncionario(in);
        assertEquals(Boolean.FALSE, f.getIpsActivo());
        assertEquals(Boolean.FALSE, f.getCobraBanco());
    }

    @Test
    void sin_el_flag_se_ignora_el_bloque_aunque_traiga_datos() {
        FuncionarioInput in = updateSinLegajo();
        in.setNumeroIps("OTRO");
        in.setFechaIngresoIps("2026-05-05");
        in.setCuentaBancaria("OTRA");
        Funcionario f = resolver.saveFuncionario(in);
        assertBloqueIpsIntacto(f);
        assertBloqueBancoIntacto(f);
    }

    @Test
    void update_mixto_aplica_ips_y_conserva_banco() {
        FuncionarioInput in = updateSinLegajo();
        in.setIpsActivo(false);
        Funcionario f = resolver.saveFuncionario(in);
        assertEquals(Boolean.FALSE, f.getIpsActivo());
        assertNull(f.getNumeroIps());
        assertNull(f.getFechaIngresoIps());
        assertBloqueBancoIntacto(f);
    }
}
