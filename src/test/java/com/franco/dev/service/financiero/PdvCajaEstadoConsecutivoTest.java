package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MonedaBilletes;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.eventos.EventosCaja;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #607: el cierre de la caja anterior contra la apertura de esta se compara moneda por moneda, con
 * medio centavo de tolerancia. Antes se sumaban todas las monedas juntas y se comparaba el double exacto.
 */
class PdvCajaEstadoConsecutivoTest {

    private static final String CON = "CON_DIFERENCIA", SIN = "SIN_DIFERENCIA";

    @Test
    void billetesDeDiezCentavosContraUnoDeTreintaNoEsDiferencia() {
        // viejo: 0.3 != 3 x 0.1 = 0.30000000000000004 -> CON
        assertEquals(SIN, PdvCajaService.estadoPorMoneda(lista(b("REAL", 1, 0.3)), lista(b("REAL", 3, 0.1))));
    }

    @Test
    void cienRealesQueVuelvenComoCienDolaresEsDiferencia() {
        // viejo: 100 == 100 -> SIN
        assertEquals(CON, PdvCajaService.estadoPorMoneda(lista(b("REAL", 1, 100)), lista(b("DOLAR", 1, 100))));
    }

    @Test
    void unFaltanteEnGuaraniesNoSeCompensaConReales() {
        // viejo: 100.000 + 0 == 95.000 + 5.000 -> SIN
        List<ConteoMoneda> cierre = lista(b("GUARANI", 1, 100_000));
        List<ConteoMoneda> apertura = lista(b("GUARANI", 1, 95_000), b("REAL", 1, 5_000));
        assertEquals(CON, PdvCajaService.estadoPorMoneda(cierre, apertura));
    }

    @Test
    void unCentavoEsDiferencia() {
        assertEquals(CON, PdvCajaService.estadoPorMoneda(lista(b("REAL", 1, 10)), lista(b("REAL", 1, 10.01))));
    }

    @Test
    void conteosIgualesEnLasTresMonedasNoSonDiferencia() {
        List<ConteoMoneda> cierre = lista(b("GUARANI", 3, 50_000), b("REAL", 2, 20), b("DOLAR", 1, 5));
        List<ConteoMoneda> apertura = lista(b("DOLAR", 1, 5), b("GUARANI", 3, 50_000), b("REAL", 2, 20));
        assertEquals(SIN, PdvCajaService.estadoPorMoneda(cierre, apertura));
    }

    @Test
    void unaMonedaDeUnSoloLado() {
        assertEquals(SIN, PdvCajaService.estadoPorMoneda(lista(b("GUARANI", 1, 1000), b("REAL", 0, 50)),
                lista(b("GUARANI", 1, 1000))), "con total 0 cuenta como si no estuviera");
        assertEquals(CON, PdvCajaService.estadoPorMoneda(lista(b("GUARANI", 1, 1000), b("DOLAR", 1, 1)),
                lista(b("GUARANI", 1, 1000))));
    }

    @Test
    void listasVacias() {
        assertEquals(SIN, PdvCajaService.estadoPorMoneda(Collections.emptyList(), Collections.emptyList()));
        assertEquals(CON, PdvCajaService.estadoPorMoneda(lista(b("GUARANI", 1, 1000)), Collections.emptyList()));
    }

    @Test
    void unBilleteConDatosNulosSeSalteaSinRomper() {
        // viejo: NPE en cantidad, valor o moneda nulos
        ConteoMoneda sinCantidad = b("GUARANI", 1, 5000);
        sinCantidad.setCantidad(null);
        ConteoMoneda sinValor = b("GUARANI", 1, 5000);
        sinValor.getMonedaBilletes().setValor(null);
        ConteoMoneda sinDenominacion = b(null, 1, 5000);
        ConteoMoneda sinBillete = new ConteoMoneda();
        List<ConteoMoneda> apertura = lista(b("GUARANI", 1, 1000), sinCantidad, sinValor, sinDenominacion, sinBillete);

        assertEquals(SIN, PdvCajaService.estadoPorMoneda(lista(b("GUARANI", 1, 1000)), apertura));
    }

    @Test
    void daLoMismoQueElEventoDeCajaAbierta() {
        Object[][] casos = {
                {lista(b("REAL", 1, 0.3)), lista(b("REAL", 3, 0.1))},
                {lista(b("REAL", 1, 100)), lista(b("DOLAR", 1, 100))},
                {lista(b("GUARANI", 1, 100_000)), lista(b("GUARANI", 1, 95_000), b("REAL", 1, 5_000))},
                {lista(b("GUARANI", 2, 50_000), b("DOLAR", 3, 1)), lista(b("GUARANI", 1, 100_000), b("DOLAR", 1, 3))},
        };
        for (Object[] c : casos) {
            @SuppressWarnings("unchecked") List<ConteoMoneda> cierre = (List<ConteoMoneda>) c[0];
            @SuppressWarnings("unchecked") List<ConteoMoneda> apertura = (List<ConteoMoneda>) c[1];
            Map<String, Double> ci = EventosCaja.totalesDeConteo(cierre, null);
            Map<String, Double> ap = EventosCaja.totalesDeConteo(apertura, null);
            EventosCaja.Anterior ant = new EventosCaja.Anterior(new PdvCaja(), ci.get("GUARANI"), ci.get("REAL"), ci.get("DOLAR"));
            String evento = EventosCaja.estadoConsecutivo(ap.get("GUARANI"), ap.get("REAL"), ap.get("DOLAR"), ant);

            assertEquals(evento, PdvCajaService.estadoPorMoneda(cierre, apertura));
        }
    }

    private static List<ConteoMoneda> lista(ConteoMoneda... cms) {
        return new ArrayList<>(Arrays.asList(cms));
    }

    private static ConteoMoneda b(String denominacion, double cantidad, double valor) {
        Moneda m = new Moneda();
        m.setDenominacion(denominacion);
        MonedaBilletes mb = new MonedaBilletes();
        mb.setMoneda(m);
        mb.setValor(valor);
        ConteoMoneda cm = new ConteoMoneda();
        cm.setCantidad(cantidad);
        cm.setMonedaBilletes(mb);
        return cm;
    }
}
