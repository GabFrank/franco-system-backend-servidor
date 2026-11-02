package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class KudeQrTest {

    /** CDC del KuDE de MARVI S.R.L. (factura 001-001-0067495 del 08/10/2026), consultado en SIFEN con 0422. */
    static final String CDC_MARVI = "01800625072001001006749522026100813312781711";

    @Test
    void elCdcSueltoDiceDeQuienEsLaFactura() {
        KudeQr qr = KudeQr.leer(CDC_MARVI);
        assertTrue(qr.isOk());
        assertEquals(1, qr.getTipoDocumento());
        assertEquals("80062507", qr.getRucEmisor());
        assertEquals("2", qr.getDvEmisor());
        assertEquals("001", qr.getEstablecimiento());
        assertEquals("001", qr.getPuntoExpedicion());
        assertEquals("0067495", qr.getNumero());
        assertEquals(LocalDate.of(2026, 10, 8), qr.getFechaEmision());
        assertNull(qr.getRucReceptor(), "el CDC suelto no trae el receptor");
    }

    @Test
    void elCdcImpresoConEspaciosTambienSirve() {
        KudeQr qr = KudeQr.leer("0180 0625 0720 0100 1006 7495 2202 6100 8133 1278 1711");
        assertTrue(qr.isOk());
        assertEquals(CDC_MARVI, qr.getCdc());
    }

    @Test
    void laUrlDelQrDaElCdcYElRucDelReceptor() {
        String url = "https://ekuatia.set.gov.py/consultas/qr?nVersion=150&Id=" + CDC_MARVI
                + "&dFeEmiDE=323032362d31302d30385431333a33303a3030&dRucRec=80099482&dTotGralOpe=8812800"
                + "&dTotIVA=801164&cItems=20&DigestValue=abc&IdCSC=0001&cHashQR=def";
        KudeQr qr = KudeQr.leer(url);
        assertTrue(qr.isOk());
        assertEquals(CDC_MARVI, qr.getCdc());
        assertEquals("80099482", qr.getRucReceptor());
    }

    @Test
    void laUrlConAmpEscapadoComoSaleDelXmlTambienSirve() {
        KudeQr qr = KudeQr.leer("https://ekuatia.set.gov.py/consultas/qr?nVersion=150&amp;Id=" + CDC_MARVI
                + "&amp;dRucRec=80099482");
        assertTrue(qr.isOk());
        assertEquals("80099482", qr.getRucReceptor());
    }

    @Test
    void unDigitoCambiadoSeRechazaPorElDigitoVerificador() {
        String malo = CDC_MARVI.substring(0, 20) + "9" + CDC_MARVI.substring(21);
        KudeQr qr = KudeQr.leer(malo);
        assertFalse(qr.isOk());
        assertEquals(KudeQr.Motivo.DIGITO_VERIFICADOR, qr.getMotivo());
    }

    @Test
    void unCodigoDeBarrasNoEsUnKude() {
        KudeQr qr = KudeQr.leer("7841503002169");
        assertFalse(qr.isOk());
        assertEquals(KudeQr.Motivo.NO_ES_KUDE, qr.getMotivo());
        assertNotNull(qr.getMensaje());
    }

    @Test
    void otroQrConParametrosPeroSinIdNoEsUnKude() {
        KudeQr qr = KudeQr.leer("https://bodega.app.frcsuite.com/x?tipo=retiro&codigo=55");
        assertFalse(qr.isOk());
        assertEquals(KudeQr.Motivo.NO_ES_KUDE, qr.getMotivo());
    }

    @Test
    void vacioSeRechazaSinExcepcion() {
        assertEquals(KudeQr.Motivo.VACIO, KudeQr.leer("  ").getMotivo());
        assertEquals(KudeQr.Motivo.VACIO, KudeQr.leer(null).getMotivo());
    }

    @Test
    void digitoVerificadorModulo11() {
        // RUC de la empresa: 80099482-5
        assertEquals(5, KudeQr.digitoVerificador("80099482"));
        // RUC de MARVI: 80062507-2
        assertEquals(2, KudeQr.digitoVerificador("80062507"));
    }
}
