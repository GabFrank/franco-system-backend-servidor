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

    @Test
    void aprendidoPorDescripcionTieneSuEtiquetaYNoAlertaComoCodigo() {
        assertEquals("Aprendido por descripción",
                ComprasMobileService.TipoMatch.etiqueta(ComprasMobileService.TipoMatch.APRENDIDO_DESCRIPCION.name()));
        assertFalse(ComprasMobileService.reconocidoPorCodigo(ComprasMobileService.TipoMatch.APRENDIDO_DESCRIPCION.name()));
        assertFalse(ComprasMobileService.reconocidoPorCodigo(ComprasMobileService.TipoMatch.APRENDIDO.name()));
    }
}
