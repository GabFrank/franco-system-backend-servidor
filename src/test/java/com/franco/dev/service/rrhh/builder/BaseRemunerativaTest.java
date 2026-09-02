package com.franco.dev.service.rrhh.builder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class BaseRemunerativaTest {

    private static BaseRemunerativa.Mes m(int mes, String monto) {
        return new BaseRemunerativa.Mes(mes, new BigDecimal(monto));
    }

    @Test
    void sumaLoPercibidoYCuentaLosMeses() {
        BaseRemunerativa.Resultado r = BaseRemunerativa.de(Arrays.asList(
                m(1, "3000000"), m(2, "3000000"), m(3, "3500000")));
        assertEquals(0, new BigDecimal("9500000").compareTo(r.total));
        assertEquals(3, r.mesesConLiquidacion);
        assertEquals(1, r.primerMes);
        assertEquals(3, r.ultimoMes);
    }

    /**
     * Que falte el mes en curso NO es un hueco. Si avisara por eso, avisaria todos los
     * meses del anio y nadie miraria el aviso cuando importe.
     */
    @Test
    void laColaFaltanteNoEsHueco() {
        BaseRemunerativa.Resultado r = BaseRemunerativa.de(Arrays.asList(
                m(1, "3000000"), m(2, "3000000"), m(3, "3000000")));
        assertFalse(r.hayHueco, "meses 1-2-3 en marzo no deberia marcar hueco");
    }

    /** Un mes salteado en el medio si: significa una liquidacion sin cargar. */
    @Test
    void unMesSalteadoEnElMedioEsHueco() {
        BaseRemunerativa.Resultado r = BaseRemunerativa.de(Arrays.asList(
                m(1, "3000000"), m(3, "3000000"), m(4, "3000000")));
        assertTrue(r.hayHueco, "falta febrero entre enero y abril");
        assertEquals(3, r.mesesConLiquidacion);
    }

    @Test
    void sinLiquidacionesQuedaVacioYNoRompe() {
        BaseRemunerativa.Resultado r = BaseRemunerativa.de(Collections.emptyList());
        assertTrue(r.vacio());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.total));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.promedioMensual()));
        assertFalse(r.hayHueco);
        assertEquals(0, BigDecimal.ZERO.compareTo(BaseRemunerativa.aguinaldoProyectado(r, 12)));
    }

    /** Un anio completo: el aguinaldo es un doceavo de todo lo percibido. */
    @Test
    void anioCompletoDaUnDoceavo() {
        BaseRemunerativa.Mes[] doce = new BaseRemunerativa.Mes[12];
        for (int i = 1; i <= 12; i++) doce[i - 1] = m(i, "3000000");
        BaseRemunerativa.Resultado r = BaseRemunerativa.de(Arrays.asList(doce));
        assertEquals(0, new BigDecimal("3000000").compareTo(BaseRemunerativa.aguinaldo(r.total)),
                "12 meses de 3.000.000 tienen que dar un aguinaldo de 3.000.000");
        assertEquals(12, r.mesesConLiquidacion);
    }

    /**
     * Con el anio ya terminado, el proyectado y el devengado tienen que coincidir. Si no,
     * en diciembre la pantalla mostraria dos numeros distintos para lo mismo.
     */
    @Test
    void conElAnioTerminadoProyectadoYDevengadoCoinciden() {
        BaseRemunerativa.Mes[] doce = new BaseRemunerativa.Mes[12];
        for (int i = 1; i <= 12; i++) doce[i - 1] = m(i, "2680373");
        BaseRemunerativa.Resultado r = BaseRemunerativa.de(Arrays.asList(doce));
        assertEquals(0, BaseRemunerativa.aguinaldo(r.total)
                        .compareTo(BaseRemunerativa.aguinaldoProyectado(r, 12)),
                "devengado y proyectado difieren con el anio completo");
    }

    /**
     * A mitad de anio el proyectado extiende el promedio observado. Con 6 meses de
     * 3.000.000 el proyectado al 31/12 tiene que ser 3.000.000, no 1.500.000.
     */
    @Test
    void elProyectadoExtiendeElPromedioObservado() {
        BaseRemunerativa.Resultado r = BaseRemunerativa.de(Arrays.asList(
                m(1, "3000000"), m(2, "3000000"), m(3, "3000000"),
                m(4, "3000000"), m(5, "3000000"), m(6, "3000000")));
        assertEquals(0, new BigDecimal("1500000").compareTo(BaseRemunerativa.aguinaldo(r.total)),
                "el devengado a junio es medio aguinaldo");
        assertEquals(0, new BigDecimal("3000000").compareTo(BaseRemunerativa.aguinaldoProyectado(r, 12)),
                "el proyectado al 31/12 tiene que ser el aguinaldo entero");
    }

    /** Un aumento a mitad de anio tiene que verse en el promedio, no perderse. */
    @Test
    void unAumentoAMitadDeAnioEntraAlPromedio() {
        BaseRemunerativa.Resultado r = BaseRemunerativa.de(Arrays.asList(
                m(1, "2000000"), m(2, "2000000"), m(3, "4000000"), m(4, "4000000")));
        assertEquals(0, new BigDecimal("3000000").compareTo(r.promedioMensual()));
    }

    @Test
    void elMesSaleDelPeriodo() {
        assertEquals(1, BaseRemunerativa.mesDePeriodo("2026-01"));
        assertEquals(12, BaseRemunerativa.mesDePeriodo("2026-12"));
        assertEquals(0, BaseRemunerativa.mesDePeriodo("2026-13"));
        assertEquals(0, BaseRemunerativa.mesDePeriodo("basura"));
        assertEquals(0, BaseRemunerativa.mesDePeriodo(null));
    }
}
