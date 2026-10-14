package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * La marca «recibe envíos» de una caja: la columna es NOT NULL, así que una caja nunca se guarda con la
 * marca en null, y cambiarla no reescribe el resto de la caja.
 */
class CajaVirtualRecibeEnviosTest {

    private CajaVirtualRepository repository;
    private CajaVirtualService service;

    @BeforeEach
    void setUp() {
        repository = mock(CajaVirtualRepository.class);
        service = mock(CajaVirtualService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "repository", repository);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void una_caja_nueva_nace_sin_la_marca_y_no_con_null() {
        assertEquals(Boolean.FALSE, new CajaVirtual().getRecibeEnvios());
    }

    @Test
    void guardar_una_caja_armada_sin_la_marca_la_deja_en_false() {
        CajaVirtual caja = new CajaVirtual();
        caja.setRecibeEnvios(null);   // un mapper o un JSON que no trae el campo

        CajaVirtual guardada = service.save(caja);

        assertEquals(Boolean.FALSE, guardada.getRecibeEnvios());
    }

    @Test
    void guardar_no_le_saca_la_marca_a_una_caja_que_la_tiene() {
        CajaVirtual caja = new CajaVirtual();
        caja.setRecibeEnvios(true);

        assertEquals(Boolean.TRUE, service.save(caja).getRecibeEnvios());
    }

    @Test
    void marcar_cambia_solo_la_marca_con_un_update_dirigido() {
        CajaVirtual caja = new CajaVirtual();
        caja.setRecibeEnvios(true);
        when(repository.marcarRecibeEnvios(18L, true)).thenReturn(1);
        when(repository.findById(18L)).thenReturn(Optional.of(caja));

        assertSame(caja, service.marcarRecibeEnvios(18L, true));

        // No se guarda la entidad entera: pisaría los saldos si un movimiento los cambió mientras tanto.
        verify(repository, never()).save(any());
    }

    @Test
    void marcar_una_caja_que_no_existe_lo_dice() {
        when(repository.marcarRecibeEnvios(99L, true)).thenReturn(0);

        assertThrows(GraphQLException.class, () -> service.marcarRecibeEnvios(99L, true));
    }
}
