package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroVerificacion;
import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.repository.financiero.RetiroSituacion;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Cancelar / habilitar un retiro (issue #376): el pedido dice cómo tiene que quedar, así que repetirlo
 * no lo invierte, y un retiro que ya entró a la caja mayor no se cancela.
 */
class RetiroServiceTest {

    private RetiroRepository repository;
    private ApplicationEventPublisher publisher;
    private RetiroVerificacionRepository verificacionRepository;
    private RetiroService service;

    /** Lo que hay en la base: el mock la mantiene para que un segundo pedido vea lo que dejó el primero. */
    private EstadoRetiro estado;
    private Long movimientoId;
    private Long cajaMayorId;

    @BeforeEach
    void setUp() {
        repository = mock(RetiroRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        verificacionRepository = mock(RetiroVerificacionRepository.class);
        service = new RetiroService(repository, publisher, verificacionRepository);

        // La instancia que devuelve el lock queda a propósito con el estado viejo (nulo): si el
        // servicio decidiera con ella, los tests de repetición fallarían.
        when(repository.lockByIdAndSucursalId(7L, 1L)).thenReturn(Optional.of(new Retiro()));
        when(repository.findSituacion(7L, 1L))
                .thenAnswer(i -> Optional.of(new RetiroSituacion(estado, movimientoId, cajaMayorId)));
        when(repository.marcarCancelado(7L, 1L)).thenAnswer(i -> { estado = EstadoRetiro.CANCELADO; return 1; });
        when(repository.marcarConcluido(7L, 1L)).thenAnswer(i -> { estado = EstadoRetiro.CONCLUIDO; return 1; });
        when(verificacionRepository.findVigente(7L, 1L)).thenReturn(Optional.empty());
    }

    private void assertNoSeEscribio() {
        verify(repository, never()).marcarCancelado(anyLong(), anyLong());
        verify(repository, never()).marcarConcluido(anyLong(), anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void cancelar_dos_veces_lo_deja_cancelado_y_escribe_una_sola_vez() {
        assertTrue(service.cancelarRetiro(7L, 1L, true));
        assertTrue(service.cancelarRetiro(7L, 1L, true));

        assertEquals(EstadoRetiro.CANCELADO, estado);
        verify(repository, times(1)).marcarCancelado(7L, 1L);
        verify(repository, never()).marcarConcluido(anyLong(), anyLong());
    }

    @Test
    void habilitar_dos_veces_lo_deja_habilitado_y_escribe_una_sola_vez() {
        estado = EstadoRetiro.CANCELADO;

        assertTrue(service.cancelarRetiro(7L, 1L, false));
        assertTrue(service.cancelarRetiro(7L, 1L, false));

        assertEquals(EstadoRetiro.CONCLUIDO, estado);
        verify(repository, times(1)).marcarConcluido(7L, 1L);
        verify(repository, never()).marcarCancelado(anyLong(), anyLong());
    }

    @Test
    void sin_argumento_cancela_y_repetido_se_rechaza_en_vez_de_habilitar() {
        assertTrue(service.cancelarRetiro(7L, 1L, null));
        assertEquals(EstadoRetiro.CANCELADO, estado);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, null));

        assertTrue(e.getMessage().contains("ya está cancelado") && e.getMessage().contains("actualizá"), e.getMessage());
        assertEquals(EstadoRetiro.CANCELADO, estado);
        verify(repository, never()).marcarConcluido(anyLong(), anyLong());
    }

    @Test
    void toma_el_lock_antes_de_leer_el_estado() {
        service.cancelarRetiro(7L, 1L, true);

        InOrder orden = inOrder(repository);
        orden.verify(repository).lockByIdAndSucursalId(7L, 1L);
        orden.verify(repository).findSituacion(7L, 1L);
        orden.verify(repository).marcarCancelado(7L, 1L);
    }

    @Test
    void no_guarda_la_entidad_ni_avisa_retiro_realizado() {
        service.cancelarRetiro(7L, 1L, true);
        service.cancelarRetiro(7L, 1L, false);

        // Guardar la entidad reescribe la fila entera; y save() del servicio publica «RETIRO REALIZADO».
        verify(repository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void un_retiro_con_su_ingreso_en_la_caja_mayor_no_se_cancela() {
        movimientoId = 114L;
        cajaMayorId = 1L;

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));

        assertTrue(e.getMessage().contains("ya entró a la caja mayor"), e.getMessage());
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_contado_en_cero_tampoco_se_cancela() {
        movimientoId = -1L;   // verificado sin plata: no hay movimiento, pero entró

        assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_con_verificacion_vigente_no_se_cancela_aunque_sus_campos_esten_limpios() {
        when(verificacionRepository.findVigente(7L, 1L)).thenReturn(Optional.of(new RetiroVerificacion()));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));

        assertTrue(e.getMessage().contains("anulá primero su verificación"), e.getMessage());
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_en_estado_verificado_no_se_cancela_ni_sin_argumento() {
        estado = EstadoRetiro.VERIFICADO_CONCLUIDO_CON_PROBLEMA;

        assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));
        assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, null));
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_con_caja_mayor_asignada_y_sin_movimiento_no_se_cancela_y_no_habla_de_verificacion() {
        cajaMayorId = 1L;

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));

        assertTrue(e.getMessage().contains("caja mayor asignada"), e.getMessage());
        assertFalse(e.getMessage().contains("verificación"), e.getMessage());
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_todavia_abierto_en_el_pdv_no_se_cancela() {
        estado = EstadoRetiro.EN_PROCESO;

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));

        assertTrue(e.getMessage().contains("EN_PROCESO"), e.getMessage());
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_concluido_se_cancela() {
        estado = EstadoRetiro.CONCLUIDO;

        service.cancelarRetiro(7L, 1L, true);

        assertEquals(EstadoRetiro.CANCELADO, estado);
    }

    @Test
    void habilitar_un_verificado_no_lo_baja_a_concluido() {
        estado = EstadoRetiro.VERIFICADO_CONCLUIDO_SIN_PROBLEMA;
        movimientoId = 114L;

        assertTrue(service.cancelarRetiro(7L, 1L, false));

        assertEquals(EstadoRetiro.VERIFICADO_CONCLUIDO_SIN_PROBLEMA, estado);
        assertNoSeEscribio();
    }

    @Test
    void un_cancelado_que_ya_tiene_su_ingreso_se_puede_habilitar() {
        // El caso que existe en producción: cancelado con la plata en la caja mayor. Habilitarlo lo arregla.
        estado = EstadoRetiro.CANCELADO;
        movimientoId = 114L;
        cajaMayorId = 1L;

        assertTrue(service.cancelarRetiro(7L, 1L, false));

        assertEquals(EstadoRetiro.CONCLUIDO, estado);
    }

    @Test
    void un_retiro_que_no_existe_lo_dice() {
        when(repository.lockByIdAndSucursalId(99L, 1L)).thenReturn(Optional.empty());

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(99L, 1L, true));

        assertTrue(e.getMessage().contains("no encontrado"), e.getMessage());
    }
}
