package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.repository.financiero.CambioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Fija la conversion con la cotizacion del comercio: pasa por el guarani, redondea una sola vez al
 * final a los decimales de la moneda destino, nunca lee las columnas de mercado y, sin cotizacion,
 * no inventa una.
 */
class ConversorMonedaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 2, 10, 0);

    private CambioRepository cambioRepository;
    private CotizacionService cotizacionService;
    private ConversorMoneda conversor;
    private Moneda guarani;
    private Moneda dolar;
    private Moneda real;

    @BeforeEach
    void setUp() {
        cambioRepository = mock(CambioRepository.class);
        cotizacionService = new CotizacionService(cambioRepository);
        conversor = new ConversorMoneda(cotizacionService);
        guarani = moneda(1L, "GUARANI", "PYG", 0);
        dolar = moneda(2L, "DOLAR", "USD", 2);
        real = moneda(3L, "REAL", "BRL", 2);
        cotizacion(dolar, 5700.0);
        cotizacion(real, 1113.28125); // 5.700 / 5,12
    }

    @Test
    void laMismaMoneda_noConvierteNiRedondeaDistinto() {
        assertEquals(new BigDecimal("12.34"), conversor.convertir(new BigDecimal("12.34"), dolar, dolar, AHORA).get());
        verifyNoInteractions(cambioRepository);
    }

    @Test
    void guaraniADolar_alCentavo() {
        assertEquals(new BigDecimal("4.39"), conversor.convertir(new BigDecimal("25000"), guarani, dolar, AHORA).get());
    }

    @Test
    void dolarAGuarani_sinDecimales() {
        assertEquals(new BigDecimal("25593"), conversor.convertir(new BigDecimal("4.49"), dolar, guarani, AHORA).get());
    }

    @Test
    void realADolar_pasaPorElGuarani_sinRedondearEnElMedio() {
        // 512 BRL * 1113,28125 Gs / 5700 Gs = 100,00 USD exactos: el ejemplo del documento.
        assertEquals(new BigDecimal("100.00"), conversor.convertir(new BigDecimal("512"), real, dolar, AHORA).get());
    }

    @Test
    void sinRedondear_conservaLaPrecisionParaSeguirCalculando() {
        BigDecimal v = conversor.convertirSinRedondear(new BigDecimal("500"), guarani, dolar, AHORA).get();
        assertEquals(0, v.compareTo(new BigDecimal("500").divide(new BigDecimal("5700"), java.math.MathContext.DECIMAL128)));
    }

    @Test
    void sinCotizacion_devuelveVacio() {
        when(cambioRepository.findVigenteDelComercio(eq(3L), any())).thenReturn(null);
        assertFalse(conversor.convertir(new BigDecimal("10"), real, dolar, AHORA).isPresent());
    }

    @Test
    void unaCotizacionCeroEsComoNoTenerla() {
        cotizacion(dolar, 0.0);
        assertFalse(conversor.convertir(new BigDecimal("10"), guarani, dolar, AHORA).isPresent());
    }

    @Test
    void nuncaLeeLasColumnasDeMercado() {
        Cambio c = new Cambio();
        c.setValorEnGs(5700.0);
        c.setValorEnGsCompraMercado(9999.0);
        c.setValorEnGsVentaMercado(9999.0);
        when(cambioRepository.findVigenteDelComercio(eq(2L), any())).thenReturn(c);
        assertEquals(new BigDecimal("5700.0"), cotizacionService.delComercio(dolar, AHORA).get());
    }

    @Test
    void elGuaraniVale1SinConsultar() {
        assertEquals(BigDecimal.ONE, cotizacionService.delComercio(guarani, AHORA).get());
        verify(cambioRepository, never()).findVigenteDelComercio(eq(1L), any());
    }

    @Test
    void laCotizacionSeBuscaAlInstantePedido() {
        conversor.convertir(BigDecimal.TEN, guarani, dolar, AHORA);
        verify(cambioRepository).findVigenteDelComercio(2L, AHORA);
    }

    private void cotizacion(Moneda m, double gs) {
        Cambio c = new Cambio();
        c.setValorEnGs(gs);
        when(cambioRepository.findVigenteDelComercio(eq(m.getId()), any())).thenReturn(c);
    }

    private static Moneda moneda(Long id, String den, String iso, Integer decimales) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(den);
        m.setCodigoIso(iso);
        m.setDecimales(decimales);
        return m;
    }
}
