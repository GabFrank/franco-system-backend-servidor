package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.NotificacionTipoEstado;
import com.franco.dev.repository.configuracion.NotificacionTipoEstadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La regla que sostiene todo el interruptor: <b>fila ausente = activo</b>.
 *
 * <p>
 * Si se invirtiera, un tipo nuevo naceria apagado y nadie se enteraria hasta
 * que alguien preguntara por que no llego el aviso.
 */
class NotificacionTipoEstadoServiceTest {

    private NotificacionTipoEstadoRepository repository;
    private NotificacionTipoEstadoService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificacionTipoEstadoRepository.class);
        service = new NotificacionTipoEstadoService();
        ReflectionTestUtils.setField(service, "repository", repository);
    }

    @Test
    void sinFila_estaActivo() {
        when(repository.findByTipoNotificacion("TIPO_NUEVO")).thenReturn(Optional.empty());

        assertTrue(service.estaActivo("TIPO_NUEVO"));
    }

    @Test
    void conFilaApagada_noEstaActivo() {
        when(repository.findByTipoNotificacion("RETIRO")).thenReturn(Optional.of(estado(false)));

        assertFalse(service.estaActivo("RETIRO"));
    }

    @Test
    void conFilaPrendida_estaActivo() {
        when(repository.findByTipoNotificacion("GASTO")).thenReturn(Optional.of(estado(true)));

        assertTrue(service.estaActivo("GASTO"));
    }

    @Test
    void tipoVacio_noBloquea() {
        // Un request sin tipo no es razon para comerse el aviso.
        assertTrue(service.estaActivo(null));
        assertTrue(service.estaActivo("   "));
    }

    private NotificacionTipoEstado estado(boolean activo) {
        NotificacionTipoEstado e = new NotificacionTipoEstado();
        e.setActivo(activo);
        return e;
    }
}
