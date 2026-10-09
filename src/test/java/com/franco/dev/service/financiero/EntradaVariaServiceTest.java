package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EntradaVaria;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.repository.financiero.EntradaVariaRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Anulacion de una entrada/salida varia: con lock y con el estado leido de la base (issue #376). */
class EntradaVariaServiceTest {

    private EntradaVariaRepository repository;
    private TesoreriaService tesoreriaService;
    private ComprobanteNumeracionService numeracion;
    private EntradaVariaService service;

    private MovimientoCajaVirtual movimiento;

    @BeforeEach
    void setUp() {
        repository = mock(EntradaVariaRepository.class);
        tesoreriaService = mock(TesoreriaService.class);
        numeracion = mock(ComprobanteNumeracionService.class);
        service = new EntradaVariaService(repository, tesoreriaService, numeracion);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        movimiento = new MovimientoCajaVirtual();
        movimiento.setId(50L);
        when(tesoreriaService.findMovimiento(50L)).thenReturn(movimiento);
    }

    private EntradaVaria entradaBloqueada(long id, Boolean anulado) {
        EntradaVaria e = new EntradaVaria();
        e.setId(id);
        e.setAnulado(anulado);
        e.setMovimientoCajaVirtualId(50L);
        when(repository.lockById(id)).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    void anular_toma_la_entrada_con_lock_lee_el_estado_y_recien_despues_revierte() {
        EntradaVaria e = entradaBloqueada(3L, false);

        EntradaVaria r = service.anular(3L, "error de carga", null);

        InOrder orden = inOrder(repository, tesoreriaService);
        orden.verify(repository).lockById(3L);
        orden.verify(repository).findAnuladoById(3L);
        orden.verify(tesoreriaService).revertir(eq(movimiento), eq("error de carga"), any());
        assertTrue(r.getAnulado());
        verify(repository).save(e);
        verify(repository, never()).findById(any());
    }

    @Test
    void anular_una_entrada_mas_vieja_que_el_limite_se_rechaza_sin_revertir() {
        // Issue #370.
        EntradaVaria vieja = entradaBloqueada(5L, false);
        java.time.LocalDateTime fecha = java.time.LocalDateTime.now().minusDays(40);
        vieja.setCreadoEn(fecha);
        org.mockito.Mockito.doThrow(new GraphQLException("TOPE")).when(tesoreriaService)
                .requireDentroDelLimiteDeAnulacion(eq(fecha), eq("La entrada/salida #5"));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(5L, null, null));

        assertEquals("TOPE", e.getMessage());
        verify(tesoreriaService, never()).revertir(any(), any(), any());
        assertFalse(vieja.getAnulado());
        verify(repository, never()).save(any());
    }

    @Test
    void anular_una_entrada_ya_anulada_se_rechaza_sin_revertir() {
        entradaBloqueada(4L, true);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(4L, null, null));

        assertTrue(e.getMessage().contains("ya está anulada"), e.getMessage());
        verify(tesoreriaService, never()).revertir(any(), any(), any());
    }

    @Test
    void anular_mira_el_estado_de_la_base_y_no_el_de_la_instancia_ya_cargada() {
        // Otra anulacion commiteo mientras esta esperaba el lock: la instancia sigue diciendo que no.
        EntradaVaria entrada = entradaBloqueada(5L, false);
        when(repository.findAnuladoById(5L)).thenReturn(Optional.of(true));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(5L, null, null));

        assertTrue(e.getMessage().contains("ya está anulada"), e.getMessage());
        verify(tesoreriaService, never()).revertir(any(), any(), any());
        verify(repository, never()).save(entrada);
    }

    @Test
    void anular_con_anulado_nulo_la_trata_como_vigente() {
        EntradaVaria entrada = entradaBloqueada(6L, null);

        service.anular(6L, null, null);

        verify(tesoreriaService).revertir(eq(movimiento), any(), any());
        assertTrue(entrada.getAnulado());
    }

    @Test
    void si_el_movimiento_ya_esta_revertido_la_entrada_no_queda_anulada() {
        EntradaVaria entrada = entradaBloqueada(7L, false);
        when(tesoreriaService.revertir(eq(movimiento), any(), any()))
                .thenThrow(new GraphQLException("El movimiento #50 ya está anulado."));

        assertThrows(GraphQLException.class, () -> service.anular(7L, null, null));

        assertFalse(Boolean.TRUE.equals(entrada.getAnulado()));
        verify(repository, never()).save(entrada);
    }

    @Test
    void anular_una_entrada_inexistente_lo_dice() {
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(404L, null, null));
        assertTrue(e.getMessage().contains("no encontrada"), e.getMessage());
    }

    @Test
    void registrar_guarda_el_comprobante_que_resuelve_la_numeracion_y_lo_pide_antes_de_guardar() {
        com.franco.dev.domain.financiero.CajaVirtual caja = new com.franco.dev.domain.financiero.CajaVirtual();
        caja.setId(1L);
        EntradaVaria e = new EntradaVaria();
        e.setCajaVirtual(caja);
        e.setEsIngreso(true);
        e.setMonto(java.math.BigDecimal.TEN);
        e.setNumeroComprobante("");   // lo que manda el desktop cuando no se tipea nada
        when(numeracion.resolver(eq("ENTRADA_VARIA"), eq(""), any(), eq("una entrada varia"))).thenReturn("EV-0007");
        when(tesoreriaService.registrar(any())).thenReturn(movimiento);

        EntradaVaria guardada = service.registrar(e, null);

        assertEquals("EV-0007", guardada.getNumeroComprobante());
        org.mockito.InOrder orden = inOrder(numeracion, repository, tesoreriaService);
        orden.verify(numeracion).resolver(eq("ENTRADA_VARIA"), eq(""), any(), eq("una entrada varia"));
        orden.verify(repository).save(any());
        orden.verify(tesoreriaService).registrar(any());
    }

    @Test
    void registrar_con_un_comprobante_repetido_no_guarda_nada() {
        com.franco.dev.domain.financiero.CajaVirtual caja = new com.franco.dev.domain.financiero.CajaVirtual();
        caja.setId(1L);
        EntradaVaria e = new EntradaVaria();
        e.setCajaVirtual(caja);
        e.setEsIngreso(true);
        e.setMonto(java.math.BigDecimal.TEN);
        e.setNumeroComprobante("REC-1");
        when(numeracion.resolver(any(), any(), any(), any()))
                .thenThrow(new graphql.GraphQLException("Ya existe una entrada varia con el comprobante REC-1."));

        assertThrows(graphql.GraphQLException.class, () -> service.registrar(e, null));

        verify(repository, never()).save(any());
        verify(tesoreriaService, never()).registrar(any());
    }
}
