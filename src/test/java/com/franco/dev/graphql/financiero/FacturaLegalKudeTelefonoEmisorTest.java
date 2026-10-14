package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El KuDE de factura imprime el mismo telefono del emisor que viaja en el XML (dTelEmi): el del
 * detalle del timbrado y, si esta vacio o en blanco, el de la cabecera.
 */
class FacturaLegalKudeTelefonoEmisorTest {

    @Test
    void elDelDetalleManda() {
        assertEquals("021123456", FacturaLegalGraphQL.telefonoEmisorKude(detalle("021123456", "0982700027")));
    }

    @Test
    void sinTelefonoEnElDetalleUsaElDeLaCabecera() {
        assertEquals("0982700027", FacturaLegalGraphQL.telefonoEmisorKude(detalle(null, "0982700027")));
    }

    @Test
    void detalleVacioOEnBlancoUsaElDeLaCabecera() {
        assertEquals("0982700027", FacturaLegalGraphQL.telefonoEmisorKude(detalle("", "0982700027")));
        assertEquals("0982700027", FacturaLegalGraphQL.telefonoEmisorKude(detalle("   ", "0982700027")));
    }

    @Test
    void seImprimeSinEspaciosAlrededor() {
        assertEquals("021123456", FacturaLegalGraphQL.telefonoEmisorKude(detalle(" 021123456 ", "0982700027")));
        assertEquals("0982700027", FacturaLegalGraphQL.telefonoEmisorKude(detalle(null, " 0982700027 ")));
    }

    @Test
    void sinTelefonoEnNingunLadoQuedaVacio() {
        assertEquals("", FacturaLegalGraphQL.telefonoEmisorKude(detalle(null, null)));
        assertEquals("", FacturaLegalGraphQL.telefonoEmisorKude(detalle("  ", "")));
        assertEquals("", FacturaLegalGraphQL.telefonoEmisorKude(detalle(null, "  ")));
    }

    private static TimbradoDetalle detalle(String telefonoDetalle, String telefonoCabecera) {
        Timbrado timbrado = new Timbrado();
        timbrado.setTelefono(telefonoCabecera);
        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setTimbrado(timbrado);
        detalle.setTelefono(telefonoDetalle);
        return detalle;
    }
}
