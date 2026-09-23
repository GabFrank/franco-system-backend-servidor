package com.franco.dev.service.activos.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * El resumen financiero de un activo lo lee alguien que esta por decidir cuanta plata pedir.
 * "No debe nada" y "no hay datos cargados" son respuestas distintas, y devolver cero para la
 * segunda afirma algo que nadie dijo: un vehiculo recien dado de alta, sin financiacion, hacia
 * que la PWA mostrara "Pendiente: 0,00".
 */
class ActivoPagoNormalizerPendienteTest {

    @Test
    void sinMontoTotalNoHayPendienteQueInformar() {
        assertNull(ActivoPagoNormalizer.montoPendienteInformable(null, null));
    }

    @Test
    void sinMontoTotalTampocoSeInventaUnCeroAunqueHayaAlgoPagado() {
        assertNull(ActivoPagoNormalizer.montoPendienteInformable(null, new BigDecimal("500")));
    }

    @Test
    void conMontoTotalInformaLoQueFalta() {
        assertEquals(0, new BigDecimal("45000000").compareTo(
                ActivoPagoNormalizer.montoPendienteInformable(
                        new BigDecimal("60000000"), new BigDecimal("15000000"))));
    }

    @Test
    void unActivoSaldadoInformaCero() {
        // Acá el cero sí es una afirmacion respaldada: hay total y esta todo pagado.
        assertEquals(0, BigDecimal.ZERO.compareTo(
                ActivoPagoNormalizer.montoPendienteInformable(
                        new BigDecimal("100"), new BigDecimal("100"))));
    }

    @Test
    void pagadoDeMasNoDevuelveNegativo() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                ActivoPagoNormalizer.montoPendienteInformable(
                        new BigDecimal("100"), new BigDecimal("150"))));
    }

    @Test
    void elCalculoInternoSigueDevolviendoNumeroParaDebeMarcarComoPagado() {
        // `calcularMontoPendiente` no cambia: `debeMarcarComoPagado` lo compara sin
        // chequear null.
        assertEquals(0, BigDecimal.ZERO.compareTo(
                ActivoPagoNormalizer.calcularMontoPendiente(null, null)));
    }
}
