package com.franco.dev.service.impresion;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.domain.financiero.VentaTarjeta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.franco.dev.service.impresion.ImpresionService.formatMontoEscaneado;
import static com.franco.dev.service.impresion.ImpresionService.simboloDeMoneda;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reporte impreso de ventas con tarjeta: el monto leido del cupon y la moneda de cada fila.
 *
 * El cupon de PlugPay ({@code USD 146.50}) es el caso que lo motivo (2026-09-24): el monto escaneado
 * salia 147 y la moneda salia de la terminal, no del registro.
 */
class ImpresionServiceVentaTarjetaTest {

    private static Moneda moneda(String simbolo) {
        Moneda m = new Moneda();
        m.setSimbolo(simbolo);
        return m;
    }

    @Test
    void montoEscaneadoEnDolaresConservaLosCentavos() {
        assertEquals("146,50", formatMontoEscaneado(new BigDecimal("146.50"), "US$"));
        assertEquals("1.146,50", formatMontoEscaneado(new BigDecimal("1146.5"), "US$"));
    }

    @Test
    void montoEscaneadoEnGuaraniesSinDecimales() {
        assertEquals("918.957", formatMontoEscaneado(new BigDecimal("918957"), "Gs."));
        assertEquals("3.500", formatMontoEscaneado(new BigDecimal("3500.00"), "Gs"));
    }

    @Test
    void montoEscaneadoNuloEsGuion() {
        assertEquals("-", formatMontoEscaneado(null, "US$"));
    }

    @Test
    void laMonedaEsLaDelRegistroAunqueLaTerminalDigaOtra() {
        TerminalPos terminal = new TerminalPos();
        terminal.setMoneda(moneda("Gs."));
        VentaTarjeta vt = new VentaTarjeta();
        vt.setTerminalPos(terminal);
        vt.setMoneda(moneda("US$"));
        assertEquals("US$", simboloDeMoneda(vt));
    }

    @Test
    void sinMonedaEnElRegistroCaeALaDeLaTerminal() {
        TerminalPos terminal = new TerminalPos();
        terminal.setMoneda(moneda("R$"));
        VentaTarjeta vt = new VentaTarjeta();
        vt.setTerminalPos(terminal);
        assertEquals("R$", simboloDeMoneda(vt));
    }

    @Test
    void sinNingunaMonedaEsGuarani() {
        assertEquals("Gs.", simboloDeMoneda(new VentaTarjeta()));
    }
}
