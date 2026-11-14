package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.OperacionFinanciera;
import com.franco.dev.domain.financiero.enums.TipoOperacionFinanciera;
import org.junit.jupiter.api.Test;

import static com.franco.dev.service.financiero.OperacionFinancieraDescripcion.Lado.DESTINO;
import static com.franco.dev.service.financiero.OperacionFinancieraDescripcion.Lado.ORIGEN;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Descripción de las dos patas de una operación financiera (desk #721). */
class OperacionFinancieraDescripcionTest {

    private static CajaVirtual caja(String nombre) {
        CajaVirtual c = new CajaVirtual();
        c.setNombre(nombre);
        return c;
    }

    private static CuentaBancaria cuenta(String banco, String numero) {
        Banco b = new Banco();
        b.setNombre(banco);
        CuentaBancaria c = new CuentaBancaria();
        c.setBanco(b);
        c.setNumero(numero);
        return c;
    }

    private static OperacionFinanciera op(TipoOperacionFinanciera tipo, String comprobante) {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setId(116L);
        op.setTipoOperacion(tipo);
        op.setNumeroComprobante(comprobante);
        return op;
    }

    @Test
    void deposito_con_boleta_nombra_la_otra_punta_en_cada_pata() {
        OperacionFinanciera op = op(TipoOperacionFinanciera.DEPOSITO_BANCARIO, "2443309");
        op.setCajaMayorOrigen(caja("COFRE CENTRAL"));
        op.setCuentaBancariaDestino(cuenta("BNF", "019001922421"));
        assertEquals("Depósito · Boleta 2443309 · COFRE CENTRAL", OperacionFinancieraDescripcion.de(op, DESTINO));
        assertEquals("Depósito · Boleta 2443309 · BNF 019001922421", OperacionFinancieraDescripcion.de(op, ORIGEN));
    }

    @Test
    void sin_comprobante_conserva_la_referencia_a_la_operacion() {
        OperacionFinanciera op = op(TipoOperacionFinanciera.DEPOSITO_BANCARIO, "  ");
        op.setCajaMayorOrigen(caja("COFRE CENTRAL"));
        op.setCuentaBancariaDestino(cuenta("BNF", "019001922421"));
        assertEquals("Depósito (op #116) · COFRE CENTRAL", OperacionFinancieraDescripcion.de(op, DESTINO));
    }

    @Test
    void retiro_y_transferencias_usan_comp() {
        OperacionFinanciera r = op(TipoOperacionFinanciera.RETIRO_BANCARIO, "77");
        r.setCuentaBancariaOrigen(cuenta("ITAU", "123"));
        r.setCajaMayorDestino(caja("CAJA MAYOR"));
        assertEquals("Retiro bancario · Comp. 77 · ITAU 123", OperacionFinancieraDescripcion.de(r, DESTINO));

        OperacionFinanciera t = op(TipoOperacionFinanciera.TRANSFERENCIA_ENTRE_CAJAS, null);
        t.setCajaMayorOrigen(caja("CAJA A"));
        t.setCajaMayorDestino(caja("CAJA B"));
        assertEquals("Transf. entre cajas (op #116) · CAJA B", OperacionFinancieraDescripcion.de(t, ORIGEN));
        assertEquals("Transf. entre cajas (op #116) · CAJA A", OperacionFinancieraDescripcion.de(t, DESTINO));
    }

    @Test
    void cambio_de_divisa_en_la_misma_caja() {
        OperacionFinanciera c = op(TipoOperacionFinanciera.CAMBIO_DIVISA, "9");
        c.setCajaMayorOrigen(caja("CAJA A"));
        c.setCajaMayorDestino(caja("CAJA A"));
        assertEquals("Cambio de divisa · Comp. 9 · CAJA A", OperacionFinancieraDescripcion.de(c, ORIGEN));
    }
}
