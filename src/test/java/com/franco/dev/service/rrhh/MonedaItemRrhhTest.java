package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.repository.financiero.CambioRepository;
import com.franco.dev.service.financiero.ConversorMoneda;
import com.franco.dev.service.financiero.CotizacionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonedaItemRrhhTest {

    private final Moneda gs = moneda(1L, "GUARANI", 0);
    private final Moneda real = moneda(2L, "REAL", 2);

    private static Moneda moneda(Long id, String denominacion, int decimales) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setDecimales(decimales);
        return m;
    }

    private MonedaItemRrhh en(Moneda destino) {
        CambioRepository repo = mock(CambioRepository.class);
        Cambio c = new Cambio();
        c.setValorEnGs(1450d);
        when(repo.findVigenteDelComercio(eq(2L), any())).thenReturn(c);
        return new MonedaItemRrhh(new ConversorMoneda(new CotizacionService(repo)), destino, LocalDateTime.now());
    }

    @Test
    void mismaMonedaQuedaTalCual() {
        MonedaItemRrhh.Cotizado c = en(gs).cotizar(new BigDecimal("270000.50"), gs, RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("270000.50").compareTo(c.monto));
        assertEquals(0, BigDecimal.ONE.compareTo(c.cotizacion));
    }

    @Test
    void sinMonedaDeOrigenEsLaDelDocumento() {
        MonedaItemRrhh.Cotizado c = en(gs).cotizar(BigDecimal.TEN, null, RoundingMode.HALF_UP);
        assertSame(gs, c.moneda);
        assertEquals(0, BigDecimal.TEN.compareTo(c.monto));
    }

    @Test
    void deGuaraniesAReales_yVueltaSinDesvio() {
        MonedaItemRrhh m = en(real);
        MonedaItemRrhh.Cotizado c = m.cotizar(new BigDecimal("159500"), gs, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("110.00"), c.monto);
        // El factor queda sin redondear: volver da exactamente lo de origen.
        assertEquals(0, new BigDecimal("159500").compareTo(MonedaItemRrhh.aOrigen(c.monto, c.cotizacion, gs)));
    }

    @Test
    void elTopeEnGuaraniesRedondeaHaciaAbajo() {
        MonedaItemRrhh m = en(real);
        // R$ 100,01 = 145.014,50 Gs: hacia abajo, para no pasarse del neto.
        assertEquals(new BigDecimal("145014"), m.aGuaranies(new BigDecimal("100.01"), gs));
        // Una cuota de 145.015 Gs vuelve como R$ 100,01 (hacia abajo), no 100,02.
        assertEquals(new BigDecimal("100.01"), m.cotizar(new BigDecimal("145015"), gs, RoundingMode.DOWN).monto);
    }

    @Test
    void sinCotizacionLanza() {
        MonedaItemRrhh m = new MonedaItemRrhh(new ConversorMoneda(new CotizacionService(mock(CambioRepository.class))),
                gs, LocalDateTime.now());
        assertThrows(MonedaItemRrhh.CotizacionFaltante.class, () -> m.cotizar(BigDecimal.TEN, real, RoundingMode.HALF_UP));
    }
}
