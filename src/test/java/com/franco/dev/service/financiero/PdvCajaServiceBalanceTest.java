package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MonedaBilletes;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * #604: la diferencia de caja en reales y dolares sale en centavos. La suma en double dejaba ruido
 * (3 x 0,1 = 0,30000000000000004) y la PWA marca «cierra con diferencia» con {@code !== 0}.
 * Solo se redondean las dos diferencias: los totales siguen crudos.
 */
class PdvCajaServiceBalanceTest {

    private static final long APERTURA = 1L, CIERRE = 2L, SUC = 24L;

    @Mock private ConteoMonedaService conteoMonedaService;
    @Mock private RetiroDetalleService retiroDetalleService;
    @Mock private GastoService gastoService;
    @Mock private com.franco.dev.service.empresarial.SucursalService sucursalService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private com.franco.dev.service.operaciones.VentaService ventaService;
    @InjectMocks private PdvCajaService service;

    private final List<ConteoMoneda> apertura = new ArrayList<>();
    private final List<ConteoMoneda> cierre = new ArrayList<>();
    /** El sumario de ventas: 0..2 venta Gs/Rs/Ds, 12..14 vuelto Gs/Rs/Ds, 15 total general. */
    private final Object[] sumario = new Object[16];

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(conteoMonedaService.findByConteoId(eq(APERTURA), anyLong())).thenReturn(apertura);
        when(conteoMonedaService.findByConteoId(eq(CIERRE), anyLong())).thenReturn(cierre);
        when(retiroDetalleService.findByCajId(anyLong(), anyLong())).thenReturn(Collections.emptyList());
        when(gastoService.findByCajaId(anyLong(), anyLong())).thenReturn(Collections.emptyList());
        List<Object[]> filas = new ArrayList<>();
        filas.add(sumario);
        when(ventaService.getRepository().sumarioVentasPorCajaAndSurusal(anyLong(), anyLong())).thenReturn(filas);
    }

    @Test
    void faltanteEnDolaresSaleEnCentavosExactos() {
        billete(apertura, "DOLAR", 1, 0);
        billete(cierre, "DOLAR", 1, 27);
        sumario[2] = 27.57;

        // viejo: 27 - 27.57 = -0.5700000000000003
        assertEquals(-0.57, balance().getDiferenciaDs(), 0.0);
    }

    @Test
    void sobranteEnDolaresSaleEnCentavosExactos() {
        billete(apertura, "DOLAR", 1, 0);
        billete(cierre, "DOLAR", 1, 28.14);
        sumario[2] = 27.57;

        assertEquals(0.57, balance().getDiferenciaDs(), 0.0);
    }

    @Test
    void cajaQueCuadraEnDolaresDaCeroYLosTotalesSiguenCrudos() {
        billete(apertura, "DOLAR", 1, 0);
        billete(cierre, "DOLAR", 3, 0.1);
        sumario[2] = 0.3;

        PdvCajaBalanceDto b = balance();

        // viejo: 0.30000000000000004 - 0.3 = 5.55e-17
        assertEquals(0.0, b.getDiferenciaDs(), 0.0);
        assertTrue(Double.compare(0.0, b.getDiferenciaDs()) == 0, "+0.0, no -0.0");
        assertEquals(3 * 0.1, b.getTotalDsCierre(), 0.0);
    }

    @Test
    void vueltoViejoConFraccionDeCentavoNoDejaDiferenciaEnReales() {
        billete(apertura, "REAL", 2, 50);
        billete(cierre, "REAL", 2, 50);
        sumario[13] = -0.00333;

        PdvCajaBalanceDto b = balance();

        // viejo: 100 - 100 - (-0.00333) = 0.00333
        assertEquals(0.0, b.getDiferenciaRs(), 0.0);
        assertEquals(-0.00333, b.getVueltoRs(), 0.0);
    }

    @Test
    void guaraniesSinCambio() {
        billete(apertura, "GUARANI", 1, 100_000);
        billete(cierre, "GUARANI", 1, 130_500);
        sumario[0] = 40_000.0;

        assertEquals(-9_500.0, balance().getDiferenciaGs(), 0.0);
    }

    @Test
    void centavosRedondeaMedioCentavoAlejandoseDelCeroYDejaPasarLoInvalido() {
        assertEquals(0.01, PdvCajaService.centavos(0.005), 0.0);
        assertEquals(-0.01, PdvCajaService.centavos(-0.005), 0.0);
        assertEquals(0.0, PdvCajaService.centavos(-0.0049), 0.0);
        assertNull(PdvCajaService.centavos(null));
        assertTrue(PdvCajaService.centavos(Double.NaN).isNaN());
        assertEquals(Double.POSITIVE_INFINITY, PdvCajaService.centavos(Double.POSITIVE_INFINITY), 0.0);
    }

    private PdvCajaBalanceDto balance() {
        PdvCaja caja = new PdvCaja();
        caja.setId(3016L);
        caja.setSucursalId(SUC);
        caja.setConteoApertura(conteo(APERTURA));
        caja.setConteoCierre(conteo(CIERRE));
        return service.generarBalance(caja);
    }

    private static Conteo conteo(long id) {
        Conteo c = new Conteo();
        c.setId(id);
        return c;
    }

    private static void billete(List<ConteoMoneda> lista, String denominacion, double cantidad, double valor) {
        Moneda m = new Moneda();
        m.setDenominacion(denominacion);
        MonedaBilletes mb = new MonedaBilletes();
        mb.setMoneda(m);
        mb.setValor(valor);
        ConteoMoneda cm = new ConteoMoneda();
        cm.setCantidad(cantidad);
        cm.setMonedaBilletes(mb);
        lista.add(cm);
    }
}
