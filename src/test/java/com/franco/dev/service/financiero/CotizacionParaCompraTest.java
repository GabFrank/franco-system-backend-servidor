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
import static org.mockito.Mockito.*;

/**
 * La cotizacion de compras: mercado de compra, si no mercado de venta, si no la del comercio. Es el
 * orden que tenian copiado RecepcionDocumentalService y ComprasMobileService (cuatro veces); este
 * test fija que el metodo unico da lo mismo.
 */
class CotizacionParaCompraTest {

    private static final LocalDateTime VIGENCIA = LocalDateTime.of(2026, 11, 1, 0, 0);

    private CambioRepository cambioRepository;
    private CotizacionService service;
    private Moneda guarani;
    private Moneda dolar;

    @BeforeEach
    void setUp() {
        cambioRepository = mock(CambioRepository.class);
        service = new CotizacionService(cambioRepository);
        guarani = moneda(1L, "GUARANI", null);
        dolar = moneda(2L, "DOLAR", "USD");
    }

    @Test
    void elGuaraniVale1SinLeerLaBase() {
        assertEquals(Optional.of(BigDecimal.ONE), service.paraCompra(guarani, null));
        verifyNoInteractions(cambioRepository);
    }

    @Test
    void primeroElMercadoDeCompra() {
        when(cambioRepository.findLastByCambioId(2L)).thenReturn(cambio(5700.0, 5750.0, 5720.0));
        assertEquals(0, new BigDecimal("5720").compareTo(service.paraCompra(dolar, null).get()));
    }

    @Test
    void sinMercadoDeCompraElDeVenta() {
        when(cambioRepository.findLastByCambioId(2L)).thenReturn(cambio(5700.0, 5750.0, null));
        assertEquals(0, new BigDecimal("5750").compareTo(service.paraCompra(dolar, null).get()));
    }

    @Test
    void sinMercadoLaDelComercio() {
        when(cambioRepository.findLastByCambioId(2L)).thenReturn(cambio(5700.0, null, null));
        assertEquals(0, new BigDecimal("5700").compareTo(service.paraCompra(dolar, null).get()));
        assertEquals(5700.0, service.paraCompraDouble(dolar));
    }

    @Test
    void unMercadoEnCeroNoCaeAlSiguiente() {
        // Igual que las copias: el primer valor no nulo decide, aunque sea 0. Y 0 no es cotizacion.
        when(cambioRepository.findLastByCambioId(2L)).thenReturn(cambio(5700.0, null, 0.0));
        assertFalse(service.paraCompra(dolar, null).isPresent());
    }

    @Test
    void sinFilaNoInventaUna() {
        assertFalse(service.paraCompra(dolar, null).isPresent());
        assertNull(service.paraCompraDouble(dolar));
    }

    @Test
    void conInstanteMiraHaciaAtras() {
        when(cambioRepository.findUltimoHasta(2L, VIGENCIA)).thenReturn(cambio(5600.0, null, null));
        assertEquals(0, new BigDecimal("5600").compareTo(service.paraCompra(dolar, VIGENCIA).get()));
        verify(cambioRepository, never()).findLastByCambioId(any());
    }

    private static Moneda moneda(Long id, String denominacion, String iso) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setCodigoIso(iso);
        return m;
    }

    private static Cambio cambio(Double comercio, Double ventaMercado, Double compraMercado) {
        Cambio c = new Cambio();
        c.setValorEnGs(comercio);
        c.setValorEnGsVentaMercado(ventaMercado);
        c.setValorEnGsCompraMercado(compraMercado);
        return c;
    }
}
