package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code pagoId} de un movimiento bancario: sale del detalle del pago que apunta al movimiento,
 * no del {@code origenId} (el cheque al contado de un pago lo tiene nulo). En una lectura sale de la @Formula de la
 * entidad (mismo select de la página, 0 = sin pago); solo una entidad recién guardada, sin el valor, consulta el detalle.
 */
class MovimientoBancarioFieldResolverTest {

    private PagoSolicitudDetalleRepository detalleRepository;
    private MovimientoBancarioFieldResolver resolver;

    @BeforeEach
    void setUp() {
        detalleRepository = mock(PagoSolicitudDetalleRepository.class);
        resolver = new MovimientoBancarioFieldResolver(detalleRepository);
    }

    @Test
    void devuelveElPagoDelDetalleAunqueElMovimientoNoTengaOrigenId() {
        MovimientoBancario chequeContado = new MovimientoBancario();
        chequeContado.setId(40L);
        chequeContado.setOrigenTipo("CHEQUE");
        chequeContado.setOrigenId(null);
        PagoSolicitudDetalle detalle = new PagoSolicitudDetalle();
        detalle.setPagoId(500L);
        when(detalleRepository.findFirstByMovimientoBancarioId(40L)).thenReturn(Optional.of(detalle));

        assertEquals(500L, resolver.pagoId(chequeContado));
    }

    @Test
    void devuelveNullSiNingunPagoApuntaAlMovimiento() {
        MovimientoBancario deposito = new MovimientoBancario();
        deposito.setId(41L);
        deposito.setOrigenTipo("OPERACION_FINANCIERA");
        deposito.setOrigenId(9L);
        when(detalleRepository.findFirstByMovimientoBancarioId(41L)).thenReturn(Optional.empty());

        assertNull(resolver.pagoId(deposito));
    }

    @Test
    void devuelveNullSinMovimientoOSinId() {
        assertNull(resolver.pagoId(null));
        assertNull(resolver.pagoId(new MovimientoBancario()));
        verifyNoInteractions(detalleRepository);
    }

    @Test
    void conElValorDeLaFormulaNoConsultaElDetalle() {
        MovimientoBancario conPago = new MovimientoBancario();
        conPago.setId(42L);
        conPago.setPagoIdLeido(500L);
        MovimientoBancario sinPago = new MovimientoBancario();
        sinPago.setId(43L);
        sinPago.setPagoIdLeido(0L);

        assertEquals(500L, resolver.pagoId(conPago));
        assertNull(resolver.pagoId(sinPago));
        verify(detalleRepository, never()).findFirstByMovimientoBancarioId(anyLong());
    }
}
