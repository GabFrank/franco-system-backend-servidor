package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.repository.financiero.MonedaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Fija la moneda de la empresa y el guarani: la principal, y sin ninguna marcada el guarani, por
 * codigo ISO y mientras no este cargado, por nombre y por id 1 (lo que asumia el codigo hasta hoy).
 */
class MonedaServiceEmpresaTest {

    private MonedaRepository repository;
    private MonedaService service;
    private Moneda guarani;

    @BeforeEach
    void setUp() {
        repository = mock(MonedaRepository.class);
        service = new MonedaService(repository);
        guarani = moneda(1L, "GUARANI", null, 0);
    }

    @Test
    void empresa_esLaPrincipal() {
        Moneda dolar = moneda(2L, "DOLAR", "USD", 2);
        when(repository.findFirstByPrincipalTrueOrderByIdAsc()).thenReturn(dolar);
        assertSame(dolar, service.empresa());
    }

    @Test
    void empresa_sinPrincipal_esElGuarani() {
        when(repository.findFirstByDenominacionContainingIgnoreCaseOrderByIdAsc("GUARAN")).thenReturn(guarani);
        assertSame(guarani, service.empresa());
    }

    @Test
    void guarani_primeroPorIso_despuesPorNombre_despuesPorId1() {
        Moneda porIso = moneda(9L, "GS", "PYG", 0);
        when(repository.findFirstByCodigoIsoIgnoreCase("PYG")).thenReturn(porIso);
        assertSame(porIso, service.guarani());

        when(repository.findFirstByCodigoIsoIgnoreCase("PYG")).thenReturn(null);
        when(repository.findFirstByDenominacionContainingIgnoreCaseOrderByIdAsc("GUARAN")).thenReturn(guarani);
        assertSame(guarani, service.guarani());

        when(repository.findFirstByDenominacionContainingIgnoreCaseOrderByIdAsc("GUARAN")).thenReturn(null);
        when(repository.findById(1L)).thenReturn(Optional.of(guarani));
        assertSame(guarani, service.guarani());
    }

    @Test
    void esGuarani_porIsoYSinIsoPorNombre() {
        assertTrue(MonedaService.esGuarani(moneda(1L, "GUARANI", null, null)));
        assertTrue(MonedaService.esGuarani(moneda(1L, "Guaraní", null, null)));
        assertTrue(MonedaService.esGuarani(moneda(1L, "LO QUE SEA", "pyg", null)));
        // Con ISO cargado manda el ISO, no el nombre.
        assertFalse(MonedaService.esGuarani(moneda(1L, "GUARANI", "USD", null)));
        assertFalse(MonedaService.esGuarani(moneda(2L, "DOLAR", null, null)));
        assertFalse(MonedaService.esGuarani(null));
    }

    @Test
    void decimales_elDatoYSinDato0ParaGuaraniY2ParaElResto() {
        assertEquals(3, MonedaService.decimales(moneda(2L, "DOLAR", "USD", 3)));
        assertEquals(0, MonedaService.decimales(moneda(1L, "GUARANI", null, null)));
        assertEquals(2, MonedaService.decimales(moneda(2L, "DOLAR", null, null)));
        assertEquals(2, MonedaService.decimales(null));
    }

    private static Moneda moneda(Long id, String den, String iso, Integer dec) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(den);
        m.setCodigoIso(iso);
        m.setDecimales(dec);
        return m;
    }
}
