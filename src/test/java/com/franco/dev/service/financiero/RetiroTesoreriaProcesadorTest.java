package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.repository.financiero.RetiroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Puente Retiro -> Caja Mayor (F3): idempotencia, guard anti doble-ingreso, posteo por moneda. */
class RetiroTesoreriaProcesadorTest {

    private RetiroRepository retiroRepository;
    private RetiroDetalleService retiroDetalleService;
    private CajaVirtualService cajaVirtualService;
    private TesoreriaService tesoreriaService;
    private RetiroTesoreriaProcesador procesador;

    private CajaVirtual caja;
    private Moneda gs;

    @BeforeEach
    void setUp() {
        retiroRepository = mock(RetiroRepository.class);
        retiroDetalleService = mock(RetiroDetalleService.class);
        cajaVirtualService = mock(CajaVirtualService.class);
        tesoreriaService = mock(TesoreriaService.class);
        com.franco.dev.service.empresarial.SucursalService sucursalService =
                mock(com.franco.dev.service.empresarial.SucursalService.class);
        procesador = new RetiroTesoreriaProcesador(retiroRepository, retiroDetalleService, cajaVirtualService,
                tesoreriaService, sucursalService);

        caja = new CajaVirtual(); caja.setId(5L);
        gs = new Moneda(); gs.setId(10L); gs.setDenominacion("GUARANIES");

        when(cajaVirtualService.findById(5L)).thenReturn(Optional.of(caja));
        when(retiroRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        MovimientoCajaVirtual posteado = new MovimientoCajaVirtual(); posteado.setId(777L);
        when(tesoreriaService.registrar(any())).thenReturn(posteado);
    }

    private Retiro retiro(Long movMarker) {
        Retiro r = new Retiro();
        r.setId(1L); r.setSucursalId(2L); r.setCajaVirtualId(5L);
        r.setMovimientoCajaVirtualId(movMarker); r.setEstado(EstadoRetiro.CONCLUIDO);
        return r;
    }

    /** El retiro tal como está en la base: lo que devuelve el lock y lo que lee la proyección. */
    private void enLaBase(Retiro r) {
        when(retiroRepository.lockByIdAndSucursalId(1L, 2L)).thenReturn(Optional.of(r));
        when(retiroRepository.findSituacion(1L, 2L)).thenAnswer(i -> Optional.of(
                new com.franco.dev.repository.financiero.RetiroSituacion(
                        r.getEstado(), r.getMovimientoCajaVirtualId(), r.getCajaVirtualId())));
    }

    private RetiroDetalle detalle(double cantidad) {
        RetiroDetalle d = new RetiroDetalle(); d.setCantidad(cantidad); d.setMoneda(gs);
        return d;
    }

    @Test
    void postea_ingreso_y_marca_procesado() {
        Retiro r = retiro(null);
        enLaBase(r);
        when(retiroDetalleService.findByRetiroId(1L, 2L)).thenReturn(Arrays.asList(detalle(100000), detalle(50000)));

        assertTrue(procesador.procesar(1L, 2L, null));

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreriaService).registrar(cap.capture());
        assertEquals(CajaVirtualTipoMovimiento.INGRESO, cap.getValue().getTipoMovimiento());
        assertEquals(150000.0, cap.getValue().getCantidad());
        assertEquals(OrigenMovimientoTipo.RETIRO_CAJA, cap.getValue().getOrigenTipo());
        assertEquals(777L, r.getMovimientoCajaVirtualId());
    }

    @Test
    void ya_procesado_no_reingresa() {
        Retiro r = retiro(777L);
        enLaBase(r);
        assertFalse(procesador.procesar(1L, 2L, null));
        verify(tesoreriaService, never()).registrar(any());
    }

    @Test
    void sin_detalles_marca_para_no_reintentar() {
        Retiro r = retiro(null);
        enLaBase(r);
        when(retiroDetalleService.findByRetiroId(1L, 2L)).thenReturn(Collections.emptyList());
        assertTrue(procesador.procesar(1L, 2L, null));
        verify(tesoreriaService, never()).registrar(any());
        assertEquals(-1L, r.getMovimientoCajaVirtualId());
    }

    @Test
    void un_retiro_cancelado_no_se_ingresa_aunque_la_instancia_cargada_diga_otra_cosa() {
        Retiro r = retiro(null);   // la instancia que devuelve el lock sigue CONCLUIDO
        when(retiroRepository.lockByIdAndSucursalId(1L, 2L)).thenReturn(Optional.of(r));
        when(retiroRepository.findSituacion(1L, 2L)).thenReturn(Optional.of(
                new com.franco.dev.repository.financiero.RetiroSituacion(EstadoRetiro.CANCELADO, null, 5L)));

        assertFalse(procesador.procesar(1L, 2L, null));

        verify(tesoreriaService, never()).registrar(any());
        verify(retiroRepository, never()).save(any());
    }

    @Test
    void toma_el_retiro_con_lock_antes_de_leer_su_estado() {
        enLaBase(retiro(null));
        when(retiroDetalleService.findByRetiroId(1L, 2L)).thenReturn(Arrays.asList(detalle(100000)));

        procesador.procesar(1L, 2L, null);

        org.mockito.InOrder orden = inOrder(retiroRepository);
        orden.verify(retiroRepository).lockByIdAndSucursalId(1L, 2L);
        orden.verify(retiroRepository).findSituacion(1L, 2L);
    }
}
