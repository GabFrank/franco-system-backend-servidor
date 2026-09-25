package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class SifenTimbradoHelperTest {

    @Test
    void codigoEstablecimiento_rellenaATresDigitos() {
        assertEquals("001", SifenTimbradoHelper.codigoEstablecimiento(sucursal("1")));
        assertEquals("007", SifenTimbradoHelper.codigoEstablecimiento(sucursal("7")));
        assertEquals("012", SifenTimbradoHelper.codigoEstablecimiento(sucursal("012")));
        assertEquals("123", SifenTimbradoHelper.codigoEstablecimiento(sucursal("123")));
    }

    @Test
    void codigoEstablecimiento_caeAlDefaultSiFaltaOEsInvalido() {
        assertEquals("001", SifenTimbradoHelper.codigoEstablecimiento(null));
        assertEquals("001", SifenTimbradoHelper.codigoEstablecimiento(sucursal(null)));
        assertEquals("001", SifenTimbradoHelper.codigoEstablecimiento(sucursal("   ")));
        assertEquals("001", SifenTimbradoHelper.codigoEstablecimiento(sucursal("ABC")));
    }

    @Test
    void fechaFirmaSegura_recortaLaFechaFutura() {
        LocalDateTime futuro = LocalDateTime.now(ZoneId.of("America/Asuncion")).plusHours(2);

        LocalDateTime firma = SifenTimbradoHelper.fechaFirmaSegura(futuro);

        assertTrue(firma.isBefore(futuro), "una fecha futura no puede firmarse tal cual");
        assertTrue(firma.isBefore(LocalDateTime.now(ZoneId.of("America/Asuncion"))),
                "la firma nunca queda adelantada respecto de la hora de Paraguay");
    }

    @Test
    void fechaFirmaSegura_respetaLaFechaPasada() {
        LocalDateTime pasado = LocalDateTime.now(ZoneId.of("America/Asuncion")).minusDays(1);

        assertEquals(pasado, SifenTimbradoHelper.fechaFirmaSegura(pasado));
    }

    @Test
    void fechaFirmaSegura_sinFechaUsaElTope() {
        LocalDateTime firma = SifenTimbradoHelper.fechaFirmaSegura(null);

        assertNotNull(firma);
        assertTrue(firma.isBefore(LocalDateTime.now(ZoneId.of("America/Asuncion"))));
    }

    @Test
    void telefonoEmisor_elDelDetalleMandaYSeRecorta() {
        assertEquals("021123456", SifenTimbradoHelper.telefonoEmisor(detalle(" 021123456 ", "0982700027")));
    }

    @Test
    void telefonoEmisor_detalleVacioOEnBlancoUsaLaCabecera() {
        assertEquals("0982700027", SifenTimbradoHelper.telefonoEmisor(detalle(null, "0982700027")));
        assertEquals("0982700027", SifenTimbradoHelper.telefonoEmisor(detalle("", "0982700027")));
        assertEquals("0982700027", SifenTimbradoHelper.telefonoEmisor(detalle("   ", " 0982700027 ")));
    }

    @Test
    void telefonoEmisor_sinTelefonoEnNingunLadoEsNullNuncaVacio() {
        assertNull(SifenTimbradoHelper.telefonoEmisor(detalle("", "  ")));
        assertNull(SifenTimbradoHelper.telefonoEmisor(detalle(null, null)));
    }

    @Test
    void telefonoEmisor_sinDetalleOSinCabeceraEsNull() {
        // Solo cubre al helper: el builder ya necesita la cabecera antes (RUC y razon social).
        assertNull(SifenTimbradoHelper.telefonoEmisor(null));
        TimbradoDetalle sinCabecera = new TimbradoDetalle();
        sinCabecera.setTelefono(" ");
        assertNull(SifenTimbradoHelper.telefonoEmisor(sinCabecera));
    }

    private static TimbradoDetalle detalle(String telefonoDetalle, String telefonoCabecera) {
        Timbrado timbrado = new Timbrado();
        timbrado.setTelefono(telefonoCabecera);
        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setTimbrado(timbrado);
        detalle.setTelefono(telefonoDetalle);
        return detalle;
    }

    private static Sucursal sucursal(String codigo) {
        Sucursal sucursal = new Sucursal();
        sucursal.setCodigoEstablecimientoFactura(codigo);
        return sucursal;
    }
}
