package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.service.financiero.CotizacionService;
import com.franco.dev.service.financiero.MonedaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * La última compra está en guaraníes; los precios de la factura, en su moneda. Al vincular un renglón de una
 * factura en dólares se comparaban sin convertir: 4,86 USD contra 2.400 Gs daba «−100 % sobre la última
 * compra» y el bulto se sugería con esa misma cuenta.
 */
class UltimaCompraEnMonedaDeFacturaTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private MonedaService monedaService;
    @Mock private CotizacionService cotizacionService;
    @InjectMocks private ComprasMobileService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(jdbc.queryForList(anyString(), eq(Double.class), any())).thenReturn(Collections.singletonList(2400.0));
        Moneda usd = new Moneda();
        usd.setId(3L);
        usd.setDenominacion("DOLAR");
        usd.setCodigoIso("USD");
        Moneda gs = new Moneda();
        gs.setId(1L);
        gs.setDenominacion("GUARANI");
        when(monedaService.porCodigoIso("USD")).thenReturn(usd);
        when(monedaService.porCodigoIso("PYG")).thenReturn(gs);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static DocumentoRecibido doc(String moneda, Double cotizacion) {
        DocumentoRecibido d = new DocumentoRecibido();
        d.setMoneda(moneda);
        d.setCotizacion(cotizacion);
        return d;
    }

    @Test
    void enGuaranies_laDeSiempre() {
        assertEquals(2400.0, service.ultimoPrecioCompraEnMonedaDe(7L, doc("PYG", 1.0)));
        assertEquals(2400.0, service.ultimoPrecioCompraEnMonedaDe(7L, doc(null, null)));
        assertEquals(2400.0, service.ultimoPrecioCompraEnMonedaDe(7L, null));
        verifyNoInteractions(cotizacionService);
    }

    @Test
    void enDolares_conLaCotizacionDeLaFactura() {
        assertEquals(0.4, service.ultimoPrecioCompraEnMonedaDe(7L, doc("USD", 6000.0)), 1e-9);
    }

    @Test
    void enDolares_sinCotizacionEnLaFactura_usaLaDeCompras() {
        when(cotizacionService.paraCompraDouble(any())).thenReturn(5000.0);
        assertEquals(0.48, service.ultimoPrecioCompraEnMonedaDe(7L, doc("USD", null)), 1e-9);
    }

    @Test
    void enDolares_sinNingunaCotizacion_noCompara() {
        assertNull(service.ultimoPrecioCompraEnMonedaDe(7L, doc("USD", null)));
    }
}
