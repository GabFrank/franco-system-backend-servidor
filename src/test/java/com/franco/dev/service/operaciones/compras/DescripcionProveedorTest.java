package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La descripción del proveedor normalizada: la clave de lo aprendido cuando la factura no trae código. */
class DescripcionProveedorTest {

    @Test
    void normalizaTildesYSignos() {
        assertEquals("JABON LUX 125G", DescripcionProveedor.normalizar("Jabón  LUX 125g."));
        assertEquals("ACEITE 900 ML X 12", DescripcionProveedor.normalizar("  aceite 900-ml x 12 "));
        assertEquals("NANDU 1 2", DescripcionProveedor.normalizar("Ñandú 1/2"));
    }

    @Test
    void blancoEsNull() {
        assertNull(DescripcionProveedor.normalizar(null));
        assertNull(DescripcionProveedor.normalizar(""));
        assertNull(DescripcionProveedor.normalizar("   "));
        assertNull(DescripcionProveedor.normalizar(" .-/ "));
    }

    @Test
    void recortaA500() {
        StringBuilder largo = new StringBuilder();
        for (int i = 0; i < 120; i++) largo.append("ABCDEFGHIJ");
        assertEquals(500, DescripcionProveedor.normalizar(largo.toString()).length());
        // Si el corte cae en un espacio, no queda colgando
        StringBuilder conEspacios = new StringBuilder();
        for (int i = 0; i < 120; i++) conEspacios.append("ABCD ");
        String n = DescripcionProveedor.normalizar(conEspacios.toString());
        assertTrue(n.length() <= 500);
        assertFalse(n.endsWith(" "));
    }

    // ── Descripción parecida: la IA no siempre lee la factura de papel igual ──

    @Test
    void parecidaIgnoraElVencimientoYElPesoQueAgregaLaIA() {
        // Canindeyú: la 1.ª lectura dijo «Palmito entero 12x1», la 2.ª «Palmito entero 12x1 800g (venc. 09/29)»
        String aprendida = DescripcionProveedor.normalizar("Palmito entero 12x1");
        String leida = DescripcionProveedor.normalizar("Palmito entero 12x1 800g (venc. 09/29)");
        assertTrue(DescripcionProveedor.parecida(aprendida, leida));
        assertTrue(DescripcionProveedor.parecida(leida, aprendida));
        assertTrue(DescripcionProveedor.parecida("GALLETA DULCE LOTE 445", "GALLETA DULCE VTO 12 2027"));
    }

    @Test
    void parecidaNoMezclaOtroBulto() {
        assertFalse(DescripcionProveedor.parecida("PALMITO ENTERO 12X1", "PALMITO ENTERO 15X1 550G VENC 06 29"));
        assertFalse(DescripcionProveedor.parecida("SALADIX JAMON 6 100GR", "SALADIX JAMON 12 100GR"));
    }

    @Test
    void parecidaNoAceptaPalabrasDeMas() {
        assertFalse(DescripcionProveedor.parecida("COCA COLA", "COCA COLA ZERO"));
        assertFalse(DescripcionProveedor.parecida("PALMITO ENTERO", "PALMITO PEDAZO"));
    }

    @Test
    void parecidaConPesosDistintosNoCoincide() {
        assertFalse(DescripcionProveedor.parecida("PALMITO ENTERO 800G", "PALMITO ENTERO 425G"));
        assertTrue(DescripcionProveedor.parecida("PALMITO ENTERO 800G", "PALMITO ENTERO 800G VENC 01 29"));
    }

    @Test
    void parecidaSinDescripcionEsFalso() {
        assertFalse(DescripcionProveedor.parecida(null, "GALLETA"));
        assertFalse(DescripcionProveedor.parecida("GALLETA", null));
        // Solo vencimiento o solo peso no alcanza para reconocer nada
        assertFalse(DescripcionProveedor.parecida("VENC 09 29", "VENC 01 30"));
        assertFalse(DescripcionProveedor.parecida("800G", "800G VENC 01 29"));
    }

    @Test
    void aprendidoPorDescripcionTieneSuEtiquetaYNoAlertaComoCodigo() {
        assertEquals("Aprendido por descripción",
                ComprasMobileService.TipoMatch.etiqueta(ComprasMobileService.TipoMatch.APRENDIDO_DESCRIPCION.name()));
        assertFalse(ComprasMobileService.reconocidoPorCodigo(ComprasMobileService.TipoMatch.APRENDIDO_DESCRIPCION.name()));
        assertFalse(ComprasMobileService.reconocidoPorCodigo(ComprasMobileService.TipoMatch.APRENDIDO.name()));
    }
}
