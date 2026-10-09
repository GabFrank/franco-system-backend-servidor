package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.repository.financiero.GastoRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Cancelar / habilitar un gasto (issue #376): el pedido dice cómo tiene que quedar, así que repetirlo
 * no lo invierte.
 */
class GastoServiceTest {

    private GastoRepository repository;
    private ApplicationEventPublisher publisher;
    private GastoService service;

    /** Lo que hay en la base. */
    private Boolean cancelado;
    private Long solicitudPagoId;

    @BeforeEach
    void setUp() {
        repository = mock(GastoRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new GastoService(repository, publisher);

        // La instancia que devuelve el lock queda con el flag viejo (nulo) a propósito.
        when(repository.lockByIdAndSucursalId(5L, 1L)).thenReturn(Optional.of(new Gasto()));
        when(repository.findCanceladoYSolicitud(5L, 1L))
                .thenAnswer(i -> Collections.singletonList(new Object[]{cancelado, solicitudPagoId}));
        when(repository.marcarCancelado(eq5(), eq1(), anyBoolean()))
                .thenAnswer(i -> { cancelado = i.getArgument(2); return 1; });
    }

    private static long eq5() { return org.mockito.ArgumentMatchers.eq(5L); }
    private static long eq1() { return org.mockito.ArgumentMatchers.eq(1L); }

    private void assertNoSeEscribio() {
        verify(repository, never()).marcarCancelado(anyLong(), anyLong(), anyBoolean());
        verify(repository, never()).save(any());
    }

    @Test
    void cancelar_dos_veces_lo_deja_cancelado_y_escribe_una_sola_vez() {
        assertTrue(service.cancelarGasto(5L, 1L, true));
        assertTrue(service.cancelarGasto(5L, 1L, true));

        assertEquals(Boolean.TRUE, cancelado);
        verify(repository, times(1)).marcarCancelado(5L, 1L, true);
    }

    @Test
    void habilitar_dos_veces_lo_deja_habilitado_y_escribe_una_sola_vez() {
        cancelado = true;

        assertTrue(service.cancelarGasto(5L, 1L, false));
        assertTrue(service.cancelarGasto(5L, 1L, false));

        assertEquals(Boolean.FALSE, cancelado);
        verify(repository, times(1)).marcarCancelado(5L, 1L, false);
    }

    @Test
    void habilitar_un_gasto_nunca_cancelado_no_escribe_nada() {
        assertTrue(service.cancelarGasto(5L, 1L, false));

        assertNull(cancelado);
        assertNoSeEscribio();
    }

    @Test
    void sin_argumento_cancela_y_repetido_se_rechaza_en_vez_de_habilitar() {
        assertTrue(service.cancelarGasto(5L, 1L, null));
        assertEquals(Boolean.TRUE, cancelado);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarGasto(5L, 1L, null));

        assertTrue(e.getMessage().contains("ya está cancelado") && e.getMessage().contains("actualizá"), e.getMessage());
        assertEquals(Boolean.TRUE, cancelado);
        verify(repository, never()).marcarCancelado(5L, 1L, false);
    }

    @Test
    void toma_el_lock_antes_de_leer_el_estado() {
        service.cancelarGasto(5L, 1L, true);

        InOrder orden = inOrder(repository);
        orden.verify(repository).lockByIdAndSucursalId(5L, 1L);
        orden.verify(repository).findCanceladoYSolicitud(5L, 1L);
        orden.verify(repository).marcarCancelado(5L, 1L, true);
    }

    @Test
    void un_gasto_pagado_desde_la_caja_mayor_no_se_cancela_ni_se_habilita_desde_aca() {
        solicitudPagoId = 88L;

        GraphQLException cancelar = assertThrows(GraphQLException.class, () -> service.cancelarGasto(5L, 1L, true));
        GraphQLException habilitar = assertThrows(GraphQLException.class, () -> service.cancelarGasto(5L, 1L, false));

        assertTrue(cancelar.getMessage().contains("para cancelarlo hay que anular el pago del gasto #88"), cancelar.getMessage());
        assertTrue(habilitar.getMessage().contains("para habilitarlo"), habilitar.getMessage());
        assertNoSeEscribio();
    }

    @Test
    void no_guarda_la_entidad_ni_avisa_gasto_realizado() {
        service.cancelarGasto(5L, 1L, true);
        service.cancelarGasto(5L, 1L, false);

        // Cancelar no es realizar un gasto: no tiene que disparar la push notification, que publica
        // el override de save().
        verify(repository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void un_gasto_que_no_existe_lo_dice() {
        when(repository.lockByIdAndSucursalId(99L, 1L)).thenReturn(Optional.empty());

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarGasto(99L, 1L, true));

        assertTrue(e.getMessage().contains("no encontrado"), e.getMessage());
    }
}
