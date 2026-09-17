package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.empresarial.Sucursal;
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

    private static Sucursal sucursal(String codigo) {
        Sucursal sucursal = new Sucursal();
        sucursal.setCodigoEstablecimientoFactura(codigo);
        return sucursal;
    }
}
