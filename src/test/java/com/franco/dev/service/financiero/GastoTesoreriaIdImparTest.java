package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El gasto materializado en la sucursal 0 tiene que nacer con id IMPAR: desde la migracion
 * V223.1 el trigger rechazar_id_de_filial rechaza en el central cualquier INSERT en
 * financiero.gasto con id par (ERRCODE check_violation), porque los pares los genera el filial.
 *
 * Con el max(id)+1 pelado, el primer gasto impar dejaba el maximo en impar y de ahi en adelante
 * TODO pago de gasto pedia un id par y moria con ConstraintViolationException / constraint [null]
 * -- el trigger levanta su propia excepcion, asi que Hibernate no encuentra nombre de constraint.
 */
class GastoTesoreriaIdImparTest {

    private GastoRepository gastoRepository;
    private GastoService gastoService;
    private GastoTesoreriaService service;

    @BeforeEach
    void setUp() {
        gastoRepository = mock(GastoRepository.class);
        gastoService = mock(GastoService.class);
        service = new GastoTesoreriaService(
                mock(SolicitudPagoService.class),
                mock(TipoGastoService.class),
                mock(MonedaService.class),
                mock(ProveedorService.class),
                gastoRepository,
                gastoService);
    }

    private SolicitudPago solicitudGastoPagada() {
        SolicitudPago sp = new SolicitudPago();
        sp.setId(451L);
        sp.setTipo(TipoSolicitudPago.GASTO);
        sp.setMontoPagado(BigDecimal.valueOf(28000));
        Moneda gs = new Moneda();
        gs.setId(1L);
        sp.setMoneda(gs);
        return sp;
    }

    private Long idMaterializadoConMaximo(Long maxId) {
        when(gastoRepository.findFirstBySolicitudPagoId(451L)).thenReturn(null);
        when(gastoRepository.findMaxId(GastoTesoreriaService.SUCURSAL_SERVIDOR)).thenReturn(maxId);

        service.sincronizarDesdeSolicitudPago(solicitudGastoPagada());

        ArgumentCaptor<Gasto> captor = ArgumentCaptor.forClass(Gasto.class);
        verify(gastoService).save(captor.capture());
        return captor.getValue().getId();
    }

    @Test
    void conMaximoImparSaltaElParQueRechazaElTrigger() {
        assertEquals(155L, idMaterializadoConMaximo(153L));
    }

    @Test
    void conMaximoParTomaElImparSiguiente() {
        assertEquals(153L, idMaterializadoConMaximo(152L));
    }

    @Test
    void sinGastosPreviosEnLaSucursalCeroArrancaEnUno() {
        assertEquals(1L, idMaterializadoConMaximo(null));
    }

    @Test
    void elIdNuncaEsPar() {
        for (long max = 0; max <= 20; max++) {
            Long id = idMaterializadoConMaximo(max);
            assertTrue(id % 2 == 1, "id par rechazado por el trigger: " + id + " (max=" + max + ")");
            assertTrue(id > max, "el id tiene que ser mayor que el maximo: " + id + " (max=" + max + ")");
            setUp();
        }
    }
}
