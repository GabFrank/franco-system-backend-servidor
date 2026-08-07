package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.repository.personas.FuncionarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.QueryHints;

import javax.persistence.QueryHint;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FuncionarioServiceCascadaEstadoTest {

    private FuncionarioRepository repository;
    private UsuarioService usuarioService;
    private ClienteService clienteService;
    private FuncionarioService service;

    private Persona persona;
    private Usuario usuario;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        repository = mock(FuncionarioRepository.class);
        usuarioService = mock(UsuarioService.class);
        clienteService = mock(ClienteService.class);
        service = new FuncionarioService(repository, usuarioService, clienteService);

        persona = new Persona();
        persona.setId(7L);

        usuario = new Usuario();
        usuario.setId(70L);
        usuario.setActivo(true);

        cliente = new Cliente();
        cliente.setId(700L);
        cliente.setActivo(true);
        cliente.setTipo(TipoCliente.FUNCIONARIO);
        cliente.setCredito(50000f);

        when(usuarioService.findByPersonaId(7L)).thenReturn(usuario);
        when(clienteService.findByPersonaId(7L)).thenReturn(cliente);
        when(repository.save(any(Funcionario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Funcionario funcionario(Boolean activo) {
        Funcionario f = new Funcionario();
        f.setId(1L);
        f.setPersona(persona);
        f.setCredito(50000f);
        f.setActivo(activo);
        return f;
    }

    @Test
    void alInactivar_desactivaUsuarioYDejaClienteNormalSinCredito() {
        when(repository.findActivoById(1L)).thenReturn(true);

        Funcionario guardado = service.save(funcionario(false));

        assertFalse(usuario.getActivo());
        assertFalse(cliente.getActivo());
        assertEquals(TipoCliente.NORMAL, cliente.getTipo());
        assertEquals(0f, cliente.getCredito());
        assertEquals(0f, guardado.getCredito());
        verify(usuarioService).save(usuario);
        verify(clienteService).save(cliente);
    }

    @Test
    void alReactivar_reactivaUsuarioYClienteFuncionarioPeroDejaElCreditoEnCero() {
        when(repository.findActivoById(1L)).thenReturn(false);
        usuario.setActivo(false);
        cliente.setActivo(false);
        cliente.setTipo(TipoCliente.NORMAL);
        cliente.setCredito(0f);

        service.save(funcionario(true));

        assertTrue(usuario.getActivo());
        assertTrue(cliente.getActivo());
        assertEquals(TipoCliente.FUNCIONARIO, cliente.getTipo());
        assertEquals(0f, cliente.getCredito());
    }

    @Test
    void siElEstadoNoCambia_noTocaUsuarioNiCliente() {
        when(repository.findActivoById(1L)).thenReturn(true);

        service.save(funcionario(true));

        verify(usuarioService, never()).save(any(Usuario.class));
        verify(clienteService, never()).save(any(Cliente.class));
    }

    @Test
    void activoNuloEnLaBaseSeTrataComoActivo_yPasarloAFalseCascadea() {
        when(repository.findActivoById(1L)).thenReturn(null);

        service.save(funcionario(false));

        assertFalse(usuario.getActivo());
        assertFalse(cliente.getActivo());
    }

    @Test
    void funcionarioSinPersona_noRompeElGuardado() {
        when(repository.findActivoById(1L)).thenReturn(true);
        Funcionario f = funcionario(false);
        f.setPersona(null);

        Funcionario guardado = service.save(f);

        assertEquals(0f, guardado.getCredito());
        verify(usuarioService, never()).save(any(Usuario.class));
        verify(clienteService, never()).save(any(Cliente.class));
    }

    @Test
    void funcionarioNuevo_noCascadeaNiConsultaElEstadoAnterior() {
        Funcionario f = new Funcionario();
        f.setPersona(persona);
        f.setCredito(50000f);

        Funcionario guardado = service.save(f);

        assertTrue(guardado.getActivo());
        verify(repository, never()).findActivoById(any());
        verify(usuarioService, never()).save(any(Usuario.class));
        verify(clienteService, never()).save(any(Cliente.class));
    }

    /**
     * Regresion del bug de auto-flush: en el path real de update la entity está managed
     * y sucia con el nuevo 'activo'. Sin flushMode=COMMIT, Hibernate flushea el UPDATE
     * antes de este SELECT y activoAnterior vuelve igual al nuevo valor, salteando la
     * cascada. Los tests de arriba mockean el repository y no pueden verlo, por eso acá
     * se verifica el contrato de la consulta: debe declarar el hint que evita el flush.
     */
    @Test
    void findActivoById_debeDeclararFlushModeCommitParaNoAutoFlushear() throws Exception {
        Method metodo = FuncionarioRepository.class.getMethod("findActivoById", Long.class);
        QueryHints hints = metodo.getAnnotation(QueryHints.class);
        assertNotNull(hints, "findActivoById debe declarar @QueryHints");
        QueryHint flushMode = Arrays.stream(hints.value())
                .filter(h -> "org.hibernate.flushMode".equals(h.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(flushMode, "falta el hint org.hibernate.flushMode");
        assertEquals("COMMIT", flushMode.value());
    }
}
