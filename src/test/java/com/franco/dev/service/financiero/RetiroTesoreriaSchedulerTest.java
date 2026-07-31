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

/** Puente Retiro → Caja Mayor (F3): idempotencia, guard anti doble-ingreso, posteo por moneda. */
class RetiroTesoreriaSchedulerTest {

    private RetiroRepository retiroRepository;
    private RetiroDetalleService retiroDetalleService;
    private CajaVirtualService cajaVirtualService;
    private TesoreriaService tesoreriaService;
    private RetiroTesoreriaScheduler scheduler;

    private CajaVirtual caja;
    private Moneda gs;

    @BeforeEach
    void setUp() {
        retiroRepository = mock(RetiroRepository.class);
        retiroDetalleService = mock(RetiroDetalleService.class);
        cajaVirtualService = mock(CajaVirtualService.class);
        tesoreriaService = mock(TesoreriaService.class);
        scheduler = new RetiroTesoreriaScheduler(retiroRepository, retiroDetalleService, cajaVirtualService, tesoreriaService);

        caja = new CajaVirtual();
        caja.setId(5L);
        gs = new Moneda();
        gs.setId(10L);
        gs.setDenominacion("GUARANIES");

        when(cajaVirtualService.findById(5L)).thenReturn(Optional.of(caja));
        when(retiroRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        MovimientoCajaVirtual posteado = new MovimientoCajaVirtual();
        posteado.setId(777L);
        when(tesoreriaService.registrar(any())).thenReturn(posteado);
    }

    private Retiro retiro(Long movMarker) {
        Retiro r = new Retiro();
        r.setId(1L);
        r.setSucursalId(2L);
        r.setCajaVirtualId(5L);
        r.setMovimientoCajaVirtualId(movMarker);
        r.setEstado(EstadoRetiro.CONCLUIDO);
        return r;
    }

    private RetiroDetalle detalle(double cantidad) {
        RetiroDetalle d = new RetiroDetalle();
        d.setCantidad(cantidad);
        d.setMoneda(gs);
        return d;
    }

    @Test
    void postea_ingreso_y_marca_procesado() {
        Retiro r = retiro(null);
        when(retiroRepository.findByIdAndSucursalId(1L, 2L)).thenReturn(r);
        when(retiroDetalleService.findByRetiroId(1L, 2L))
                .thenReturn(Arrays.asList(detalle(100000), detalle(50000)));

        assertTrue(scheduler.procesar(r));

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreriaService).registrar(cap.capture());
        MovimientoCajaVirtual mov = cap.getValue();
        assertEquals(CajaVirtualTipoMovimiento.INGRESO, mov.getTipoMovimiento());
        assertEquals(150000.0, mov.getCantidad()); // agrupado por moneda
        assertEquals(OrigenMovimientoTipo.RETIRO_CAJA, mov.getOrigenTipo());
        assertEquals(777L, r.getMovimientoCajaVirtualId()); // marcado
    }

    @Test
    void ya_procesado_no_reingresa() {
        Retiro r = retiro(777L); // ya tiene marker
        when(retiroRepository.findByIdAndSucursalId(1L, 2L)).thenReturn(r);

        assertFalse(scheduler.procesar(r));
        verify(tesoreriaService, never()).registrar(any());
    }

    @Test
    void sin_detalles_marca_para_no_reintentar() {
        Retiro r = retiro(null);
        when(retiroRepository.findByIdAndSucursalId(1L, 2L)).thenReturn(r);
        when(retiroDetalleService.findByRetiroId(1L, 2L)).thenReturn(Collections.emptyList());

        assertTrue(scheduler.procesar(r));
        verify(tesoreriaService, never()).registrar(any());
        assertEquals(-1L, r.getMovimientoCajaVirtualId()); // marcado para no reintentar
    }
}
