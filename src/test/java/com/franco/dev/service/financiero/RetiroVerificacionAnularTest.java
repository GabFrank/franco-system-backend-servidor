package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroVerificacion;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.RetiroCasoRepository;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Anular una verificacion de retiro: el estado se relee despues de tomar el lock del retiro (issue #376).
 * Sin esa relectura, una segunda anulacion que esperaba el lock —con una re-verificacion en el medio—
 * revertia los movimientos de la verificacion NUEVA.
 */
class RetiroVerificacionAnularTest {

    private RetiroRepository retiroRepository;
    private RetiroVerificacionRepository verificacionRepository;
    private MovimientoCajaVirtualRepository movimientoRepository;
    private TesoreriaService tesoreriaService;
    private RetiroCasoRepository casoRepository;
    private RetiroVerificacionService service;

    private RetiroVerificacion verificacion;
    private Retiro retiro;
    private MovimientoCajaVirtual movimiento;

    @BeforeEach
    void setUp() {
        retiroRepository = mock(RetiroRepository.class);
        verificacionRepository = mock(RetiroVerificacionRepository.class);
        movimientoRepository = mock(MovimientoCajaVirtualRepository.class);
        tesoreriaService = mock(TesoreriaService.class);
        casoRepository = mock(RetiroCasoRepository.class);
        service = new RetiroVerificacionService(retiroRepository, mock(RetiroDetalleService.class),
                verificacionRepository, casoRepository, movimientoRepository, mock(CajaVirtualService.class),
                mock(MonedaService.class), tesoreriaService, mock(TesoreriaSecurityService.class),
                mock(com.franco.dev.service.empresarial.SucursalService.class));

        verificacion = new RetiroVerificacion();
        verificacion.setId(30L);
        verificacion.setRetiroId(700L);
        verificacion.setSucursalId(5L);
        verificacion.setAnulada(false);
        when(verificacionRepository.findById(30L)).thenReturn(Optional.of(verificacion));
        when(verificacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        retiro = new Retiro();
        when(retiroRepository.lockByIdAndSucursalId(700L, 5L)).thenReturn(Optional.of(retiro));

        movimiento = new MovimientoCajaVirtual();
        movimiento.setId(90L);
        when(movimientoRepository.findByOrigenTipoAndOrigenIdAndOrigenSucursalIdAndActivoTrue(
                OrigenMovimientoTipo.RETIRO_CAJA, 700L, 5L)).thenReturn(Collections.singletonList(movimiento));
    }

    @Test
    void anular_relee_el_estado_despues_del_lock_del_retiro_y_recien_ahi_revierte() {
        service.anular(30L, "conto mal", null);

        InOrder orden = inOrder(retiroRepository, verificacionRepository, tesoreriaService);
        orden.verify(retiroRepository).lockByIdAndSucursalId(700L, 5L);
        orden.verify(verificacionRepository).findAnuladaById(30L);
        orden.verify(tesoreriaService).revertir(eq(movimiento), any(), any());
        assertTrue(verificacion.getAnulada());
    }

    @Test
    void si_otra_anulacion_la_gano_mientras_esperaba_el_lock_no_revierte_los_movimientos_vigentes() {
        // La instancia leida antes del lock dice vigente; la base, despues del lock, dice anulada. Los
        // movimientos activos que devuelve la consulta son de una verificacion posterior.
        when(verificacionRepository.findAnuladaById(30L)).thenReturn(Optional.of(true));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(30L, "conto mal", null));

        assertTrue(e.getMessage().contains("ya está anulada"), e.getMessage());
        verify(tesoreriaService, never()).revertir(any(), any(), any());
        verify(retiroRepository, never()).save(any());
        verify(verificacionRepository, never()).save(any());
    }

    @Test
    void anular_una_verificacion_ya_anulada_se_rechaza() {
        verificacion.setAnulada(true);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(30L, null, null));

        assertTrue(e.getMessage().contains("ya está anulada"), e.getMessage());
        verify(tesoreriaService, never()).revertir(any(), any(), any());
    }

    @Test
    void anular_cierra_el_caso_con_un_update_dirigido_y_no_leyendolo_para_guardarlo_entero() {
        // Leer el caso y guardarlo le pisaba el veredicto a quien lo estuviera resolviendo en ese momento.
        service.anular(30L, "conto mal", null);

        verify(casoRepository).cerrarPorAnulacion(eq(30L),
                eq(com.franco.dev.domain.financiero.enums.EstadoCasoRetiro.RESUELTO),
                eq("CERRADO POR ANULACION DE LA VERIFICACION: CONTO MAL"), any(), any());
        verify(casoRepository, never()).findByVerificacionId(any());
        verify(casoRepository, never()).save(any());
    }
}
