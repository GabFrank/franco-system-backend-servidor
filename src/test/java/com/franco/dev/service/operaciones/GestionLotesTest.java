package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.enums.ModoAjusteLote;
import com.franco.dev.graphql.operaciones.input.AjusteStockLoteInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gestion de lotes (lotes flexibles, entrega 5): liberar saldo a SIN LOTE, archivar y conteo rapido.
 */
class GestionLotesTest {

    @Test
    void liberarBajaElLoteYDevuelveASinLoteSinTocarLaExistencia() {
        AjusteStockLoteService.PlanAjuste plan =
                AjusteStockLoteService.calcular(ModoAjusteLote.LIBERAR, 1021.0, 97.0);

        assertEquals(0.0, plan.getCantidadMovimiento());
        assertEquals(-924.0, plan.getCantidadLote());
        assertEquals(924.0, plan.getCantidadSinTrazar());
        // Padre = suma de hijas.
        assertEquals(plan.getCantidadMovimiento(), plan.getCantidadLote() + plan.getCantidadSinTrazar());
    }

    @Test
    void liberarTodoDejaElLoteEnCero() {
        AjusteStockLoteService.PlanAjuste plan =
                AjusteStockLoteService.calcular(ModoAjusteLote.LIBERAR, 45.0, 0.0);
        assertEquals(-45.0, plan.getCantidadLote());
        assertEquals(45.0, plan.getCantidadSinTrazar());
    }

    @Test
    void liberarNoPuedeSubirElLote() {
        assertThrows(IllegalArgumentException.class,
                () -> AjusteStockLoteService.calcular(ModoAjusteLote.LIBERAR, 10.0, 12.0));
    }

    @Test
    void liberarNoAceptaCantidadNegativa() {
        assertThrows(IllegalArgumentException.class,
                () -> AjusteStockLoteService.calcular(ModoAjusteLote.LIBERAR, 10.0, -1.0));
    }

    @Test
    void archivarConSaldoSeRechaza() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LoteService.validarArchivable(4.0, "vencido"));
        assertTrue(e.getMessage().contains("saldo"));
        // Saldo negativo en la red tambien es saldo: no se esconde.
        assertThrows(IllegalArgumentException.class, () -> LoteService.validarArchivable(-2.0, "x"));
    }

    @Test
    void archivarSinMotivoSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> LoteService.validarArchivable(0.0, " "));
        assertThrows(IllegalArgumentException.class, () -> LoteService.validarArchivable(0.0, null));
    }

    @Test
    void archivarConSaldoCeroYMotivoPasa() {
        assertDoesNotThrow(() -> LoteService.validarArchivable(0.0, "lote agotado"));
        assertDoesNotThrow(() -> LoteService.validarArchivable(0.00001, "redondeo"));
    }

    @Test
    void conteoRapidoRechazaListaVaciaYDemasiadoLarga() {
        AjusteStockLoteService service = new AjusteStockLoteService();
        assertThrows(IllegalArgumentException.class, () -> service.ajustarLista(new ArrayList<>()));
        List<AjusteStockLoteInput> muchos = new ArrayList<>();
        for (int i = 0; i <= AjusteStockLoteService.MAX_AJUSTES_POR_LISTA; i++) {
            muchos.add(new AjusteStockLoteInput());
        }
        assertThrows(IllegalArgumentException.class, () -> service.ajustarLista(muchos));
    }

    @Test
    void conteoRapidoSalteaRenglonesSinDiferencia() {
        AjusteStockLoteService service = new AjusteStockLoteService();
        AjusteStockLoteInput igual = new AjusteStockLoteInput();
        igual.setSaldoEsperado(30.0);
        igual.setCantidadFinal(30.0);
        List<AjusteStockLoteInput> lista = new ArrayList<>();
        lista.add(igual);
        // Sin dependencias: si no lo salteara, ajustar() fallaria por falta de datos.
        assertEquals(0, service.ajustarLista(lista).size());
    }
}
