package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.repository.financiero.RetiroSituacion;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Ingreso directo de un retiro a la caja mayor (issue #376): toma el retiro con lock, lee su estado de
 * la base y no ingresa uno cancelado.
 */
class RetiroIngresoServiceTest {

    private RetiroRepository retiroRepository;
    private RetiroTesoreriaProcesador procesador;
    private RetiroIngresoService service;
    private Retiro cargado;

    @BeforeEach
    void setUp() {
        retiroRepository = mock(RetiroRepository.class);
        procesador = mock(RetiroTesoreriaProcesador.class);
        CajaVirtualService cajaVirtualService = mock(CajaVirtualService.class);
        service = new RetiroIngresoService(retiroRepository, procesador, cajaVirtualService);

        cargado = new Retiro();   // lo que devuelve el lock: sin estado, sin movimiento
        when(retiroRepository.lockByIdAndSucursalId(7L, 1L)).thenReturn(Optional.of(cargado));
        when(cajaVirtualService.findById(3L)).thenReturn(Optional.of(new CajaVirtual()));
    }

    private void enLaBase(EstadoRetiro estado, Long movimientoId) {
        when(retiroRepository.findSituacion(7L, 1L)).thenReturn(Optional.of(new RetiroSituacion(estado, movimientoId, null)));
    }

    @Test
    void ingresa_un_retiro_flotante_tomandolo_con_lock_antes_de_leer_su_estado() {
        enLaBase(null, null);

        service.ingresarACajaMayor(7L, 1L, 3L, null);

        InOrder orden = inOrder(retiroRepository, procesador);
        orden.verify(retiroRepository).lockByIdAndSucursalId(7L, 1L);
        orden.verify(retiroRepository).findSituacion(7L, 1L);
        orden.verify(procesador).procesar(7L, 1L, null);
        assertEquals(3L, cargado.getCajaVirtualId());
    }

    @Test
    void un_retiro_cancelado_no_se_ingresa_aunque_la_instancia_cargada_no_lo_sepa() {
        enLaBase(EstadoRetiro.CANCELADO, null);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.ingresarACajaMayor(7L, 1L, 3L, null));

        assertTrue(e.getMessage().contains("está cancelado"), e.getMessage());
        verify(retiroRepository, never()).save(any());
        verify(procesador, never()).procesar(any(), any(), any());
    }

    @Test
    void un_retiro_ya_ingresado_se_rechaza_segun_la_base() {
        enLaBase(EstadoRetiro.CONCLUIDO, 114L);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.ingresarACajaMayor(7L, 1L, 3L, null));

        assertTrue(e.getMessage().contains("ya fue ingresado"), e.getMessage());
        verify(procesador, never()).procesar(any(), any(), any());
    }

    @Test
    void un_retiro_que_no_existe_lo_dice() {
        when(retiroRepository.lockByIdAndSucursalId(99L, 1L)).thenReturn(Optional.empty());

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.ingresarACajaMayor(99L, 1L, 3L, null));

        assertTrue(e.getMessage().contains("no encontrado"), e.getMessage());
    }
}
