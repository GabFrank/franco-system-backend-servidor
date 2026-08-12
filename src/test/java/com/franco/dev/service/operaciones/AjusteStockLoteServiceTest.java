package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.enums.ModoAjusteLote;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba la decisión del ajuste, que es la parte que puede salir mal en silencio: cuánto se escribe
 * en el movimiento agregado y cuánto en cada fila del ledger.
 *
 * Es una función pura a propósito, así que no hace falta levantar Spring ni tocar la base.
 */
class AjusteStockLoteServiceTest {

    @Test
    void corregirHaciaAbajoBajaLaExistenciaYElLote() {
        AjusteStockLoteService.PlanAjuste plan =
                AjusteStockLoteService.calcular(ModoAjusteLote.CORREGIR, 75.0, 68.0);

        assertEquals(-7.0, plan.getCantidadMovimiento());
        assertEquals(-7.0, plan.getCantidadLote());
        assertEquals(0.0, plan.getCantidadSinTrazar());
        assertFalse(plan.isVacio());
    }

    @Test
    void corregirHaciaArribaSubeLaExistenciaYElLote() {
        AjusteStockLoteService.PlanAjuste plan =
                AjusteStockLoteService.calcular(ModoAjusteLote.CORREGIR, 75.0, 80.0);

        assertEquals(5.0, plan.getCantidadMovimiento());
        assertEquals(5.0, plan.getCantidadLote());
        assertEquals(0.0, plan.getCantidadSinTrazar());
    }

    @Test
    void atribuirNoCambiaLaExistencia() {
        AjusteStockLoteService.PlanAjuste plan =
                AjusteStockLoteService.calcular(ModoAjusteLote.ATRIBUIR, 0.0, 195.0);

        assertEquals(0.0, plan.getCantidadMovimiento());
        assertEquals(195.0, plan.getCantidadLote());
        assertEquals(-195.0, plan.getCantidadSinTrazar());
    }

    @Test
    void atribuirEnNegativoDevuelveElStockAlBucketSinTrazar() {
        AjusteStockLoteService.PlanAjuste plan =
                AjusteStockLoteService.calcular(ModoAjusteLote.ATRIBUIR, 195.0, 150.0);

        assertEquals(0.0, plan.getCantidadMovimiento());
        assertEquals(-45.0, plan.getCantidadLote());
        assertEquals(45.0, plan.getCantidadSinTrazar());
    }

    @Test
    void sinDiferenciaNoEscribeNada() {
        assertTrue(AjusteStockLoteService.calcular(ModoAjusteLote.CORREGIR, 75.0, 75.0).isVacio());
        assertTrue(AjusteStockLoteService.calcular(ModoAjusteLote.ATRIBUIR, 75.0, 75.0).isVacio());
    }

    @Test
    void laDiferenciaMenorAlEpsilonNoEscribeNada() {
        assertTrue(AjusteStockLoteService.calcular(ModoAjusteLote.CORREGIR, 75.0, 75.00001).isVacio());
    }

    /**
     * El invariante del ledger: la suma de las filas hijas tiene que dar exactamente la cantidad
     * del movimiento agregado. Si esto se rompe, el stock total y el stock por lote quedan
     * contando cosas distintas, que es justo lo que este módulo existe para evitar.
     */
    @Test
    void enTodosLosCasosLasHijasSumanElMovimientoAgregado() {
        double[][] casos = {
                {75.0, 68.0}, {75.0, 80.0}, {0.0, 195.0}, {195.0, 150.0}, {0.0, -5.0}, {10.0, 0.0}
        };
        for (ModoAjusteLote modo : ModoAjusteLote.values()) {
            for (double[] caso : casos) {
                AjusteStockLoteService.PlanAjuste plan =
                        AjusteStockLoteService.calcular(modo, caso[0], caso[1]);
                assertEquals(plan.getCantidadMovimiento(),
                        plan.getCantidadLote() + plan.getCantidadSinTrazar(),
                        0.0001,
                        "modo " + modo + ", saldo " + caso[0] + ", final " + caso[1]);
            }
        }
    }
}
