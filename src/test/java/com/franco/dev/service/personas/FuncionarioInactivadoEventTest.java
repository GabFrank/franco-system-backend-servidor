package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.service.personas.event.FuncionarioInactivadoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Quedar inactivo es la senal, no el boton que lo provoco.
 *
 * <p>Al funcionario lo dan de baja cuatro caminos distintos y solo uno pasa por
 * {@code egresar()}: el que RRHH usa a diario es el pago del finiquito
 * ({@code LiquidacionFinalService}), que llama derecho a {@code save()}. Por eso el evento
 * se publica desde la transicion activo -> inactivo, que es lo unico que todos comparten.</p>
 */
class FuncionarioInactivadoEventTest {

    private static final Long FUNC_ID = 1L;

    private FuncionarioRepository repository;
    private ApplicationEventPublisher publisher;
    private FuncionarioService service;
    private Funcionario funcionario;

    @BeforeEach
    void setUp() {
        repository = mock(FuncionarioRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        ClienteService clienteService = mock(ClienteService.class);

        service = new FuncionarioService(repository, usuarioService, clienteService, publisher);

        Persona persona = new Persona();
        persona.setId(7L);

        funcionario = new Funcionario();
        funcionario.setId(FUNC_ID);
        funcionario.setPersona(persona);
        funcionario.setCredito(0f);

        when(repository.save(any(Funcionario.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findById(FUNC_ID)).thenReturn(Optional.of(funcionario));
    }

    @Test
    void publicaElEventoCuandoElFuncionarioPasaDeActivoAInactivo() {
        when(repository.findActivoById(FUNC_ID)).thenReturn(true);
        funcionario.setActivo(false);

        service.save(funcionario);

        // Ojo con el overload: Spring publica por publishEvent(ApplicationEvent), asi que un
        // captor de Object verifica un metodo que nadie llamo y el never() pasa siempre.
        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        ApplicationEvent publicado = captor.getValue();
        assertTrue(publicado instanceof FuncionarioInactivadoEvent,
                "tiene que publicarse un FuncionarioInactivadoEvent, no " + publicado.getClass().getSimpleName());
        assertEquals(FUNC_ID, ((FuncionarioInactivadoEvent) publicado).getFuncionarioId(),
                "el evento tiene que viajar con el id del funcionario dado de baja");
    }

    @Test
    void noPublicaNadaAlReactivarAlFuncionario() {
        when(repository.findActivoById(FUNC_ID)).thenReturn(false);
        funcionario.setActivo(true);

        service.save(funcionario);

        verify(publisher, never()).publishEvent(any(ApplicationEvent.class));
    }

    @Test
    void noPublicaNadaSiElFuncionarioYaEstabaInactivo() {
        when(repository.findActivoById(FUNC_ID)).thenReturn(false);
        funcionario.setActivo(false);

        service.save(funcionario);

        verify(publisher, never()).publishEvent(any(ApplicationEvent.class));
    }

    @Test
    void noPublicaNadaAlCrearUnFuncionarioNuevo() {
        Funcionario nuevo = new Funcionario();
        nuevo.setPersona(funcionario.getPersona());

        service.save(nuevo);

        verify(publisher, never()).publishEvent(any(ApplicationEvent.class));
    }

}
