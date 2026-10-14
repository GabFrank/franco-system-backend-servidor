package com.franco.dev.service.financiero;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** La huella tiene que ser estable ante un reintento rearmado y distinta ante otro pedido (issue #376). */
class HuellaPedidoTest {

    @Test
    void elMismoMontoConOtraEscalaDaLaMismaHuella() {
        assertEquals(new HuellaPedido().numero(new BigDecimal("150000")).calcular(),
                new HuellaPedido().numero(new BigDecimal("150000.00")).calcular());
        assertEquals(new HuellaPedido().numero(BigDecimal.valueOf(150000.0)).calcular(),
                new HuellaPedido().numero(new BigDecimal("1.5E+5")).calcular());
        assertEquals(new HuellaPedido().numero(BigDecimal.ZERO).calcular(),
                new HuellaPedido().numero(new BigDecimal("0.000")).calcular());
    }

    @Test
    void otroMontoDaOtraHuella() {
        assertNotEquals(new HuellaPedido().numero(new BigDecimal("150000")).calcular(),
                new HuellaPedido().numero(new BigDecimal("150001")).calcular());
    }

    @Test
    void banderaNulaValeLoMismoQueFalse() {
        assertEquals(new HuellaPedido().bandera(null).calcular(), new HuellaPedido().bandera(false).calcular());
        assertNotEquals(new HuellaPedido().bandera(null).calcular(), new HuellaPedido().bandera(true).calcular());
    }

    @Test
    void laFechaCuentaSoloPorSuDia() {
        assertEquals(new HuellaPedido().dia(LocalDateTime.of(2026, 10, 8, 9, 15, 3)).calcular(),
                new HuellaPedido().dia(LocalDateTime.of(2026, 10, 8, 23, 59, 59)).calcular());
        assertNotEquals(new HuellaPedido().dia(LocalDateTime.of(2026, 10, 8, 9, 0)).calcular(),
                new HuellaPedido().dia(LocalDateTime.of(2026, 10, 9, 9, 0)).calcular());
    }

    @Test
    void dosCamposNoSeConfundenAunqueElTextoTraigaElSeparador() {
        assertNotEquals(new HuellaPedido().texto("A|1:B").texto("").calcular(),
                new HuellaPedido().texto("A").texto("B").calcular());
        assertNotEquals(new HuellaPedido().id(12L).id(3L).calcular(),
                new HuellaPedido().id(1L).id(23L).calcular());
    }

    @Test
    void elOrdenDeLosCamposImporta() {
        assertNotEquals(new HuellaPedido().id(1L).id(2L).calcular(), new HuellaPedido().id(2L).id(1L).calcular());
    }

    @Test
    void esUnSha256EnHexa() {
        assertTrue(new HuellaPedido().texto("x").calcular().matches("[0-9a-f]{64}"));
    }
}
