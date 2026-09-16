package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.enums.PagoEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.operaciones.PagoRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Issue #304: savePago no puede cambiar a mano un pago del motor de tesoreria (CONCLUIDO, PARCIAL, CANCELADO) ni
 * asignarle esos estados: dejaria la caja sin revertir. Tampoco reescribe usuario ni fecha de creacion.
 */
class PagoServiceGuardarManualTest {

    private PagoRepository repository;
    private PagoService service;

    @BeforeEach
    void setUp() {
        repository = mock(PagoRepository.class);
        service = new PagoService(repository);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Pago pagoEnBase(PagoEstado estado, Usuario usuario, LocalDateTime creadoEn) {
        Pago p = new Pago();
        p.setId(5L);
        p.setEstado(estado);
        p.setUsuario(usuario);
        p.setCreadoEn(creadoEn);
        when(repository.findById(5L)).thenReturn(Optional.of(p));
        return p;
    }

    private static Usuario usuario(long id) {
        Usuario u = new Usuario();
        u.setId(id);
        return u;
    }

    @Test
    void no_cancela_a_mano_un_pago_concluido() {
        Pago p = pagoEnBase(PagoEstado.CONCLUIDO, usuario(1), LocalDateTime.of(2026, 9, 1, 10, 0));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.guardarManual(5L, PagoEstado.CANCELADO, false, null, null));

        assertTrue(e.getMessage().contains("desde la caja"), e.getMessage());
        assertEquals(PagoEstado.CONCLUIDO, p.getEstado());
        verify(repository, never()).save(any());
    }

    @Test
    void no_reabre_a_mano_un_pago_concluido() {
        Pago p = pagoEnBase(PagoEstado.CONCLUIDO, usuario(1), LocalDateTime.of(2026, 9, 1, 10, 0));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.guardarManual(5L, PagoEstado.ABIERTO, false, null, null));

        assertTrue(e.getMessage().contains("El pago #5 está CONCLUIDO"), e.getMessage());
        assertEquals(PagoEstado.CONCLUIDO, p.getEstado());
        verify(repository, never()).save(any());
    }

    @Test
    void no_edita_un_pago_anulado() {
        pagoEnBase(PagoEstado.CANCELADO, usuario(1), LocalDateTime.of(2026, 9, 1, 10, 0));

        assertThrows(GraphQLException.class, () -> service.guardarManual(5L, null, true, null, null));
        verify(repository, never()).save(any());
    }

    @Test
    void un_alta_no_acepta_un_estado_del_motor() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.guardarManual(null, PagoEstado.CONCLUIDO, false, usuario(1), null));

        assertTrue(e.getMessage().contains("no se asigna a mano"), e.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void finalizar_un_pago_abierto_conserva_usuario_y_fecha_de_la_base() {
        Usuario creador = usuario(1);
        LocalDateTime creado = LocalDateTime.of(2026, 9, 1, 10, 0);
        Usuario autoriza = usuario(3);
        pagoEnBase(PagoEstado.ABIERTO, creador, creado);

        Pago guardado = service.guardarManual(5L, PagoEstado.PENDIENTE, true, usuario(2), autoriza);

        assertEquals(PagoEstado.PENDIENTE, guardado.getEstado());
        assertTrue(guardado.getProgramado());
        assertSame(creador, guardado.getUsuario());
        assertEquals(creado, guardado.getCreadoEn());
        assertSame(autoriza, guardado.getAutorizadoPor());
    }

    @Test
    void un_id_inexistente_da_pago_no_encontrado() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.guardarManual(99L, PagoEstado.PENDIENTE, false, null, null));

        assertEquals("Pago no encontrado", e.getMessage());
    }

    @Test
    void un_alta_queda_abierta_con_su_usuario() {
        Usuario creador = usuario(1);

        Pago guardado = service.guardarManual(null, PagoEstado.ABIERTO, false, creador, null);

        assertEquals(PagoEstado.ABIERTO, guardado.getEstado());
        assertSame(creador, guardado.getUsuario());
        assertNotNull(guardado.getCreadoEn());
    }
}
