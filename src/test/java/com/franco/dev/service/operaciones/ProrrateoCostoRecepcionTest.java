package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug 2.0a: los costos adicionales de una recepcion se sumaban sin convertir. Un flete en Gs sobre
 * una nota en USD se prorrateaba como si fueran dolares.
 */
class ProrrateoCostoRecepcionTest {

    private static Moneda moneda(long id, String denominacion) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        return m;
    }

    private static final Moneda GS = moneda(1, "GUARANI");
    private static final Moneda REAL = moneda(2, "REAL");
    private static final Moneda USD = moneda(3, "DOLAR");

    private static NotaRecepcion nota(Moneda m, Double cotizacion) {
        NotaRecepcion n = new NotaRecepcion();
        n.setMoneda(m);
        n.setCotizacion(cotizacion);
        return n;
    }

    private static BigDecimal gs(double v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void sinAdicionalesElCostoEsElPrecioTalCual() {
        // Es el caso de toda recepcion de bodega: el resultado no puede moverse ni un decimal.
        assertEquals(1234.567, ProrrateoCostoRecepcion.costoUnitario(1234.567, BigDecimal.ZERO, gs(5_000_000)));
        assertEquals(1234.567, ProrrateoCostoRecepcion.costoUnitario(1234.567, gs(10_000), BigDecimal.ZERO));
    }

    @Test
    void todoEnGuaraniesEsElMismoRecargoDeAntes() {
        // 100.000 Gs de mercaderia + 10.000 Gs de flete: 10% a cada item.
        assertEquals(5500.0, ProrrateoCostoRecepcion.costoUnitario(5000, gs(10_000), gs(100_000)), 1e-9);
    }

    @Test
    void fleteEnGuaraniesSobreNotaEnDolares() {
        // 10 u. a 10 USD, cotizacion 7.000: 700.000 Gs. Flete 70.000 Gs: +10% => 11 USD.
        // Antes: 10 + 70.000 * (100/100) / 10 = 7.010 USD.
        BigDecimal valorGs = gs(10).multiply(gs(10)).multiply(ProrrateoCostoRecepcion.factorGs(7000.0));
        BigDecimal fleteGs = gs(70_000).multiply(
                ProrrateoCostoRecepcion.gsPorUnidad(GS, Collections.singletonList(nota(USD, 7000.0)), USD, 7000.0, "FLETE"));
        assertEquals(11.0, ProrrateoCostoRecepcion.costoUnitario(10, fleteGs, valorGs), 1e-9);
    }

    @Test
    void notasEnDosMonedasRepartenPorSuValorEnGuaranies() {
        // Nota USD: 700.000 Gs. Nota Gs: 300.000 Gs. Flete 100.000 Gs => +10% para los dos.
        BigDecimal valorGs = gs(700_000).add(gs(300_000));
        assertEquals(11.0, ProrrateoCostoRecepcion.costoUnitario(10, gs(100_000), valorGs), 1e-9);
        assertEquals(3300.0, ProrrateoCostoRecepcion.costoUnitario(3000, gs(100_000), valorGs), 1e-9);
    }

    @Test
    void elCostoEnLaMonedaDeUnaNotaUsaSuCotizacion() {
        BigDecimal f = ProrrateoCostoRecepcion.gsPorUnidad(REAL,
                Arrays.asList(nota(USD, 7000.0), nota(REAL, 1300.0)), GS, 1.0, "DESPACHO");
        assertEquals(0, gs(1300.0).compareTo(f));
    }

    @Test
    void sinNotaNiRecepcionEnEsaMonedaNoInventaCotizacion() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProrrateoCostoRecepcion.gsPorUnidad(REAL, Collections.singletonList(nota(USD, 7000.0)), USD, 7000.0, "DESPACHO"));
        assertTrue(ex.getMessage().contains("DESPACHO"));
        assertTrue(ex.getMessage().contains("REAL"));
    }

    @Test
    void laCotizacionSoloCuentaSiEsMayorAUno() {
        // La misma regla que CostoMedioCalculator.aGuaranies.
        assertEquals(0, BigDecimal.ONE.compareTo(ProrrateoCostoRecepcion.factorGs(1.0)));
        assertEquals(0, BigDecimal.ONE.compareTo(ProrrateoCostoRecepcion.factorGs(null)));
        assertEquals(0, gs(7000.0).compareTo(ProrrateoCostoRecepcion.factorGs(7000.0)));
    }
}
