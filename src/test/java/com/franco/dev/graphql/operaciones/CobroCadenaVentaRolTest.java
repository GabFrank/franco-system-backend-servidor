package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.MovimientoCajaGraphQL;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import com.franco.dev.service.operaciones.CobroService;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * saveVenta -> saveCobro -> detalle y movimiento de caja: ningun paso es transaccional, asi que un rechazo
 * a mitad de camino dejaria un cobro sin movimiento. saveCobro tiene que usar los metodos internos sin
 * control, no las entradas GraphQL (saveCobroDetalle exige VENTA TOUCH; saveMovimientoCaja, ADMIN).
 */
class CobroCadenaVentaRolTest {

    @Test
    void unCajeroSoloVentaTouchCobraEnEfectivoSinRechazoAMitadDeCamino() {
        TesoreriaSecurityFalso cajero = new TesoreriaSecurityFalso(7L, "VENTA TOUCH");

        CobroDetalleGraphQL detalles = spy(new ObjenesisStd().newInstance(CobroDetalleGraphQL.class));
        ReflectionTestUtils.setField(detalles, "seg", cajero);
        CobroDetalle efectivo = new CobroDetalle();
        efectivo.setDescuento(false);
        FormaPago fp = new FormaPago();
        fp.setDescripcion("Efectivo");
        efectivo.setFormaPago(fp);
        doReturn(efectivo).when(detalles).guardarCobroDetalle(any());

        MovimientoCajaGraphQL movimientos = spy(new ObjenesisStd().newInstance(MovimientoCajaGraphQL.class));
        ReflectionTestUtils.setField(movimientos, "seg", cajero);
        doReturn(null).when(movimientos).guardarMovimientoCaja(any());

        CobroService service = mock(CobroService.class);
        Cobro guardado = new Cobro();
        guardado.setId(1L);
        Usuario u = new Usuario();
        u.setId(7L);
        guardado.setUsuario(u);
        when(service.save(any())).thenReturn(guardado);

        CobroGraphQL cobros = new ObjenesisStd().newInstance(CobroGraphQL.class);
        ReflectionTestUtils.setField(cobros, "seg", cajero);
        ReflectionTestUtils.setField(cobros, "service", service);
        ReflectionTestUtils.setField(cobros, "cobroDetalleGraphQL", detalles);
        ReflectionTestUtils.setField(cobros, "movimientoCajaGraphQL", movimientos);

        Cobro resultado = cobros.saveCobro(new CobroInput(), Collections.singletonList(new CobroDetalleInput()), 3L);

        assertSame(guardado, resultado);
        verify(movimientos).guardarMovimientoCaja(any());
        verify(movimientos, never()).saveMovimientoCaja(any());
        verify(detalles, never()).saveCobroDetalle(any());
    }
}
