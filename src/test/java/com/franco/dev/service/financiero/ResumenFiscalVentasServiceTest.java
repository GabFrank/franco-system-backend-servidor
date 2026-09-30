package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.dto.ResumenFiscalContribuyente;
import com.franco.dev.domain.financiero.dto.ResumenFiscalTimbrado;
import com.franco.dev.domain.financiero.dto.ResumenFiscalVentas;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resumen fiscal de ventas: armado a partir de las filas agregadas de la consulta nativa
 * (una por RUC + sucursal + punto de expedicion + timbrado).
 */
class ResumenFiscalVentasServiceTest {

    private static final String RUC = "80099482-5";
    private static final String RAZON = "FRANCO AREVALOS S.A.";

    @Test
    void laBaseGravadaEsElTotalMenosElIvaYSeSumaPorContribuyente() {
        List<Object[]> filas = Arrays.asList(
                // electronica: 110.000 al 10 % (IVA 10.000), 21.000 al 5 % (IVA 1.000), 5.000 exentas
                fila(RUC, RAZON, 1L, "SUC. CENTRAL", "001", "001", "18270044", true, 3, 1, 156442, 156445,
                        "110000.40", "10000.30", "21000", "1000", "5000"),
                // papel: 55.000 al 10 % (IVA 5.000)
                fila(RUC, RAZON, 3L, "SUC. ROTONDA", "002", "003", "17599896", false, 2, 0, 88, 89,
                        "55000", "5000", null, null, null));

        ResumenFiscalVentas r = ResumenFiscalVentasService.armar(2026, 7, "Todas", filas);

        assertEquals("Julio 2026", r.getPeriodo());
        assertEquals("Todas", r.getSucursalesFiltro());
        assertEquals(1, r.getContribuyentes().size());

        ResumenFiscalContribuyente c = r.getContribuyentes().get(0);
        assertEquals(RUC, c.getRuc());
        assertEquals(RAZON, c.getRazonSocial());
        // 110.000 + 55.000 = 165.000 con IVA -> 150.000 de base y 15.000 de IVA
        assertEquals(150000d, c.getGravada10());
        assertEquals(15000d, c.getIva10());
        assertEquals(20000d, c.getGravada5());
        assertEquals(1000d, c.getIva5());
        assertEquals(5000d, c.getExentas());
        assertEquals(175000d, c.getTotalBase());
        assertEquals(16000d, c.getTotalIva());
        assertEquals(191000d, c.getTotalFacturado());
        assertEquals(5L, c.getEmitidas());
        assertEquals(1L, c.getAnuladas());

        ResumenFiscalTimbrado e = c.getDetalle().get(0);
        assertEquals("SUC. CENTRAL", e.getSucursal());
        assertEquals("Electrónica", e.getTipo());
        assertTrue(e.getElectronico());
        assertEquals("001-001-0156442", e.getNumeroDesde());
        assertEquals("001-001-0156445", e.getNumeroHasta());
        assertEquals(100000d, e.getGravada10(), "se redondea a guaranies antes de restar");
        assertEquals(10000d, e.getIva10());
        assertEquals(136000d, e.getTotalFacturado());

        ResumenFiscalTimbrado p = c.getDetalle().get(1);
        assertEquals("Papel", p.getTipo());
        assertFalse(p.getElectronico());
        assertEquals("002-003-0000088", p.getNumeroDesde());
        assertEquals(0d, p.getGravada5(), "las sumas nulas valen cero");
        assertEquals(0d, p.getExentas());
    }

    @Test
    void unaSeccionPorRucOrdenadasPorRuc() {
        ResumenFiscalVentas r = ResumenFiscalVentasService.armar(2026, 1, "SUC. CENTRAL", Arrays.asList(
                fila("99999999-1", "OTRA S.A.", 1L, "SUC. CENTRAL", "001", "002", "1", false, 1, 0, 1, 1,
                        "11000", "1000", null, null, null),
                fila(RUC, RAZON, 1L, "SUC. CENTRAL", "001", "001", "2", true, 1, 0, 1, 1,
                        "22000", "2000", null, null, null)));

        assertEquals("Enero 2026", r.getPeriodo());
        assertEquals(2, r.getContribuyentes().size());
        assertEquals(RUC, r.getContribuyentes().get(0).getRuc());
        assertEquals(20000d, r.getContribuyentes().get(0).getGravada10());
        assertEquals("99999999-1", r.getContribuyentes().get(1).getRuc());
        assertEquals(10000d, r.getContribuyentes().get(1).getGravada10());
    }

    @Test
    void sinFacturasNoHayContribuyentes() {
        ResumenFiscalVentas r = ResumenFiscalVentasService.armar(2026, 12, "Todas", Collections.emptyList());
        assertEquals("Diciembre 2026", r.getPeriodo());
        assertTrue(r.getContribuyentes().isEmpty());
    }

    @Test
    void rechazaMesYAnioInvalidos() {
        assertThrows(GraphQLException.class, () -> ResumenFiscalVentasService.validarPeriodo(2026, 13));
        assertThrows(GraphQLException.class, () -> ResumenFiscalVentasService.validarPeriodo(2026, 0));
        assertThrows(GraphQLException.class, () -> ResumenFiscalVentasService.validarPeriodo(null, 5));
    }

    /** Misma forma que devuelve FacturaLegalRepository.resumenFiscalVentas*. */
    private static Object[] fila(String ruc, String razon, Long sucId, String sucursal, String establecimiento,
                                 String punto, String timbrado, boolean electronico, long emitidas, long anuladas,
                                 long desde, long hasta, String t10, String i10, String t5, String i5, String t0) {
        return new Object[]{ruc, razon, BigDecimal.valueOf(sucId), sucursal, establecimiento, punto, timbrado,
                electronico, BigDecimal.valueOf(emitidas), BigDecimal.valueOf(anuladas),
                new BigDecimal(desde), new BigDecimal(hasta),
                dec(t10), dec(i10), dec(t5), dec(i5), dec(t0)};
    }

    private static BigDecimal dec(String v) {
        return v != null ? new BigDecimal(v) : null;
    }
}
