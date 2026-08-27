package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba la decisión de la finalización de una toma para un producto CON control de lote: qué se
 * escribe en el movimiento agregado, cuánto en cada fila del ledger y cuándo el producto queda
 * afuera del ajuste.
 *
 * Es una función pura a propósito —mismo criterio que {@link AjusteStockLoteServiceTest}—, así que
 * no hace falta levantar Spring ni tocar la base.
 *
 * Las cantidades van SIEMPRE en unidades base: el conteo llega por presentación y la finalización
 * lo convierte antes de llamar acá, igual que hace hoy con el agregado.
 */
class InventarioLoteServiceTest {

    /**
     * El caso normal: todo lo contado tiene lote, así que el total sale de sumarlos.
     * Los casos donde NO es así llaman a planificar() con el total explícito.
     */
    private static PlanConteoLote plan(double existencia, Map<Long, Double> saldos,
                                       Map<Long, Double> contado) {
        double total = 0.0;
        for (Double valor : contado.values()) {
            total += valor;
        }
        return InventarioLoteService.planificar(existencia, total, saldos, contado);
    }

    private static Map<Long, Double> saldos(Object... pares) {
        Map<Long, Double> mapa = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put(((Number) pares[i]).longValue(), ((Number) pares[i + 1]).doubleValue());
        }
        return mapa;
    }

    @Test
    void omiteElProductoCuandoUnLoteConSaldoNoSeConto() {
        // L2 tiene 20 unidades y ningún renglón lo contó: nadie fue a mirar esa mercadería.
        PlanConteoLote plan = plan(50.0, saldos(1L, 30.0, 2L, 20.0), saldos(1L, 28.0));

        assertTrue(plan.isOmitido());
        assertEquals(Collections.singletonList(2L), plan.getLotesSinContar());
    }

    @Test
    void noOmiteCuandoElLoteSinContarTieneSaldoCero() {
        // Un lote agotado no es mercadería que alguien debió contar.
        PlanConteoLote plan = plan(30.0, saldos(1L, 30.0, 2L, 0.0), saldos(1L, 30.0));

        assertFalse(plan.isOmitido());
    }

    @Test
    void elMovimientoAgregadoEsLoContadoMenosLaExistencia() {
        PlanConteoLote plan = plan(50.0, saldos(1L, 30.0), saldos(1L, 28.0));

        assertEquals(-22.0, plan.getCantidadMovimiento());
    }

    @Test
    void laFilaDelLoteEsLoContadoMenosSuSaldo() {
        PlanConteoLote plan = plan(50.0, saldos(1L, 30.0), saldos(1L, 28.0));

        assertEquals(-2.0, plan.getCantidadPorLote().get(1L));
    }

    @Test
    void elRestoNoExplicadoPorLosLotesVaALaFilaSinTrazar() {
        // Existencia 50 con un solo lote de 30: había 20 sin atribuir. Contar 28 del lote deja el
        // bucket sin trazar en cero, que es el punto de contar por lote.
        PlanConteoLote plan = plan(50.0, saldos(1L, 30.0), saldos(1L, 28.0));

        assertEquals(-20.0, plan.getCantidadSinTrazar());
    }

    @Test
    void unLoteNuevoEnGondolaAtribuyeSinCambiarLaExistencia() {
        // Aparece el lote 3, que el sistema no tenía, y explica justo las 20 sin trazar.
        PlanConteoLote plan = plan(50.0, saldos(1L, 30.0), saldos(1L, 30.0, 3L, 20.0));

        assertEquals(0.0, plan.getCantidadMovimiento());
        assertEquals(20.0, plan.getCantidadPorLote().get(3L));
        assertEquals(-20.0, plan.getCantidadSinTrazar());
    }

    @Test
    void lasHijasSumanExactamenteElPadre() {
        PlanConteoLote plan = plan(50.0, saldos(1L, 30.0, 2L, 5.0), saldos(1L, 28.0, 2L, 4.0, 3L, 9.0));

        double hijas = plan.getCantidadSinTrazar();
        for (Double valor : plan.getCantidadPorLote().values()) {
            hijas += valor;
        }
        assertEquals(plan.getCantidadMovimiento(), hijas, 0.0001);
    }

    @Test
    void loContadoSinLoteVaAlBucketSinTrazar() {
        // Un producto con control de lote puede tener un renglon contado SIN lote: es la mercaderia
        // que todavia no se atribuyo a ninguno. Cuenta para el total —y por lo tanto para el
        // movimiento agregado—, pero no puede generar una fila de lote: cae en SIN LOTE.
        // Existencia 50, lote L1 con 30. Se cuentan 30 de L1 y 15 sueltas: total 45.
        PlanConteoLote plan = InventarioLoteService.planificar(
                50.0, 45.0, saldos(1L, 30.0), saldos(1L, 30.0));

        assertEquals(-5.0, plan.getCantidadMovimiento());
        assertEquals(0.0, plan.getCantidadPorLote().get(1L));
        assertEquals(-5.0, plan.getCantidadSinTrazar());
    }

    @Test
    void sinDiferenciasNoHayNadaQueEscribir() {
        PlanConteoLote plan = plan(30.0, saldos(1L, 30.0), saldos(1L, 30.0));

        assertTrue(plan.isVacio());
    }

    @Test
    void sinNadaContadoYSinLotesNoAjustaNada() {
        // Sin esta guarda el movimiento saldría en -50 y la finalización le llevaría la existencia
        // a cero a un producto que nadie contó.
        PlanConteoLote plan = plan(50.0, Collections.emptyMap(), Collections.emptyMap());

        assertTrue(plan.isOmitido());
    }

    @Test
    void sinRenglonesContadosElProductoNoEntraAlAjuste() {
        // Ningún renglón contado no es "contado cero": es exactamente el caso que la finalización
        // ya saltea para el agregado, y llevaría el stock a cero sin que nadie mirara.
        PlanConteoLote plan = plan(50.0, saldos(1L, 30.0), Collections.emptyMap());

        assertTrue(plan.isOmitido());
    }
}
