package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.dto.PreciosEnMonedaDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ConversionPrecioServiceTest {

    @Mock private MonedaService monedaService;
    @Mock private CambioService cambioService;
    @InjectMocks private ConversionPrecioService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() { mocks = MockitoAnnotations.openMocks(this); }

    @AfterEach
    void tearDown() throws Exception { mocks.close(); }

    private Moneda moneda(long id, String denominacion, Boolean activo, Integer decimales, Double cambio) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setActivo(activo);
        m.setDecimales(decimales);
        when(cambioService.findLastValorEnGsByMonedaId(id)).thenReturn(cambio);
        return m;
    }

    @Test
    void convierteYRedondeaConLosDecimalesDeLaMoneda() {
        Moneda gs = moneda(1, "GUARANI", true, null, 1d);
        Moneda rs = moneda(2, "REAL", true, null, 1350d);
        Moneda ds = moneda(3, "DOLAR", true, 2, 7800d);
        when(monedaService.findAll2()).thenReturn(Arrays.asList(gs, rs, ds));

        List<PreciosEnMonedaDto> r = service.convertir(Arrays.asList(3500d, 42000d));

        assertEquals(3, r.size());
        assertEquals(0, r.get(0).getDecimales());
        assertEquals(Arrays.asList(3500d, 42000d), r.get(0).getMontos());
        // 3500 / 1350 = 2.5925… → 2.59 ; 42000 / 1350 = 31.111… → 31.11
        assertEquals(Arrays.asList(2.59d, 31.11d), r.get(1).getMontos());
        // 3500 / 7800 = 0.4487… → 0.45 (HALF_UP)
        assertEquals(0.45d, r.get(2).getMontos().get(0));
    }

    @Test
    void omiteLasInactivasYLasQueNoTienenCotizacion() {
        Moneda gs = moneda(1, "GUARANI", true, 0, 1d);
        Moneda inactiva = moneda(2, "REAL", false, 2, 1350d);
        Moneda sinCambio = moneda(3, "DOLAR", true, 2, null);
        Moneda cambioCero = moneda(4, "PESO ARGENTINO", null, 2, 0d);
        when(monedaService.findAll2()).thenReturn(Arrays.asList(gs, inactiva, sinCambio, cambioCero));

        List<PreciosEnMonedaDto> r = service.convertir(Arrays.asList(1000d));

        assertEquals(1, r.size());
        assertEquals("GUARANI", r.get(0).getMoneda().getDenominacion());
    }

    @Test
    void respetaElOrdenYLosNulos() {
        Moneda rs = moneda(2, "REAL", true, 2, 1000d);
        when(monedaService.findAll2()).thenReturn(Arrays.asList(rs));

        List<PreciosEnMonedaDto> r = service.convertir(Arrays.asList(5000d, null, 1000d));

        assertEquals(Arrays.asList(5d, null, 1d), r.get(0).getMontos());
    }
}
