package com.franco.dev.utilitarios;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Solo se buscan en internet los códigos que identifican al producto fuera del local. */
class Ean13UtilsCodigoComercialTest {

    @Test
    void unEan13ComercialSeBusca() {
        assertTrue(Ean13Utils.esCodigoComercial("7840050002561")); // Brahma Sub Zero lata (Paraguay)
        assertTrue(Ean13Utils.esCodigoComercial(" 7891149103102 "));
    }

    @Test
    void unUpcAyUnEan8ComercialesSeBuscan() {
        assertTrue(Ean13Utils.esCodigoComercial("036000291452")); // UPC-A
        assertTrue(Ean13Utils.esCodigoComercial("96385074"));     // EAN-8
    }

    @Test
    void losInternosGeneradosNoSeBuscan() {
        String interno = Ean13Utils.fromInternalSequence(123);
        assertTrue(Ean13Utils.isValidEan13(interno));
        assertFalse(Ean13Utils.esCodigoComercial(interno));
    }

    @Test
    void losDeCirculacionRestringidaNoSeBuscan() {
        String balanza = "200123400000";
        balanza = balanza + Ean13Utils.checkDigit(balanza);
        assertFalse(Ean13Utils.esCodigoComercial(balanza));
        assertFalse(Ean13Utils.esCodigoComercial("02345673")); // EAN-8 con 0
    }

    @Test
    void conDigitoDeControlMaloOFormatoRaroNoSeBusca() {
        assertFalse(Ean13Utils.esCodigoComercial("7840050002562"));
        assertFalse(Ean13Utils.esCodigoComercial("123"));
        assertFalse(Ean13Utils.esCodigoComercial("PROMO1"));
        assertFalse(Ean13Utils.esCodigoComercial(null));
    }
}
