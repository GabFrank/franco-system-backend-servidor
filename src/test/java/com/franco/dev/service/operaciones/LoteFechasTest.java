package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Qué fechas quedan cuando alguien corrige un lote desde el conteo.
 *
 * Es la regla más delicada del cambio: la fecha de retiro es la que ordena FEFO en TODA la red, así
 * que un tipeo acá reordena stock que ya está en góndola en otras sucursales.
 *
 * Función pura, sin Spring, igual que {@link AjusteStockLoteServiceTest}.
 */
class LoteFechasTest {

    private static final LocalDate VENCE_DICIEMBRE = LocalDate.of(2026, 12, 1);
    private static final LocalDate VENCE_ENERO = LocalDate.of(2027, 1, 10);
    private static final LocalDate RETIRO_NOVIEMBRE = LocalDate.of(2026, 11, 1);

    @Test
    void cargaLaFechaDeRetiroQueFaltaba() {
        LoteService.FechasLote fechas = LoteService.resolverFechas(
                VENCE_DICIEMBRE, null, null, RETIRO_NOVIEMBRE, null);

        assertEquals(RETIRO_NOVIEMBRE, fechas.getFechaRetiro());
        assertEquals(VENCE_DICIEMBRE, fechas.getFechaVencimiento());
    }

    @Test
    void corrigeUnaFechaDeRetiroYaCargada() {
        LocalDate corregida = LocalDate.of(2026, 10, 15);

        LoteService.FechasLote fechas = LoteService.resolverFechas(
                VENCE_DICIEMBRE, RETIRO_NOVIEMBRE, null, corregida, null);

        assertEquals(corregida, fechas.getFechaRetiro());
    }

    @Test
    void unNuloNoBorraLoQueYaEstabaCargado() {
        // El input no puede distinguir "no lo mandé" de "borralo", y borrar un vencimiento no es
        // un caso real del negocio.
        LoteService.FechasLote fechas = LoteService.resolverFechas(
                VENCE_DICIEMBRE, RETIRO_NOVIEMBRE, null, null, null);

        assertEquals(VENCE_DICIEMBRE, fechas.getFechaVencimiento());
        assertEquals(RETIRO_NOVIEMBRE, fechas.getFechaRetiro());
    }

    @Test
    void alCambiarElVencimientoRecalculaElRetiroSiNadieLoHabiaCargado() {
        LoteService.FechasLote fechas = LoteService.resolverFechas(
                VENCE_DICIEMBRE, null, VENCE_ENERO, null, 30);

        assertEquals(VENCE_ENERO, fechas.getFechaVencimiento());
        assertEquals(LocalDate.of(2026, 12, 11), fechas.getFechaRetiro());
    }

    @Test
    void noPisaElRetiroYaCargadoAlCambiarElVencimiento() {
        // No hay forma de distinguir un retiro derivado de uno tipeado a mano, así que se respeta
        // el que está: pisarlo reordenaría el FEFO sin que nadie lo pidiera.
        LoteService.FechasLote fechas = LoteService.resolverFechas(
                VENCE_DICIEMBRE, RETIRO_NOVIEMBRE, VENCE_ENERO, null, 30);

        assertEquals(RETIRO_NOVIEMBRE, fechas.getFechaRetiro());
    }

    @Test
    void sinDiasDeVencimientoNoInventaUnRetiro() {
        LoteService.FechasLote fechas = LoteService.resolverFechas(
                VENCE_DICIEMBRE, null, VENCE_ENERO, null, null);

        assertNull(fechas.getFechaRetiro());
    }

    @Test
    void alCrearUnLoteDerivaElRetiroDeLosDiasDelProducto() {
        // Es el caso de «Crear nuevo lote» desde el conteo: el operador carga el número y el
        // vencimiento que dice el envase, y la fecha de retiro sale sola.
        LoteService.FechasLote fechas = LoteService.resolverFechas(
                null, null, VENCE_ENERO, null, 30);

        assertEquals(VENCE_ENERO, fechas.getFechaVencimiento());
        assertEquals(LocalDate.of(2026, 12, 11), fechas.getFechaRetiro());
    }

    @Test
    void alCrearSinVencimientoNoHayNingunaFecha() {
        // Un lote sin vencimiento es válido: hay productos con control de lote y sin fecha.
        LoteService.FechasLote fechas = LoteService.resolverFechas(null, null, null, null, 30);

        assertNull(fechas.getFechaVencimiento());
        assertNull(fechas.getFechaRetiro());
    }

    @Test
    void rechazaUnRetiroPosteriorAlVencimiento() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LoteService.resolverFechas(
                        VENCE_DICIEMBRE, null, null, LocalDate.of(2026, 12, 2), null));

        assertEquals("La fecha de retiro no puede ser posterior al vencimiento del lote.",
                error.getMessage());
    }

    @Test
    void aceptaUnRetiroElMismoDiaDelVencimiento() {
        LoteService.FechasLote fechas = LoteService.resolverFechas(
                VENCE_DICIEMBRE, null, null, VENCE_DICIEMBRE, null);

        assertEquals(VENCE_DICIEMBRE, fechas.getFechaRetiro());
    }

    @Test
    void validaContraElVencimientoNuevoYNoContraElViejo() {
        // Se adelanta el vencimiento a octubre: un retiro de noviembre que antes era válido deja
        // de serlo, y el error tiene que salir en la misma operación.
        assertThrows(IllegalArgumentException.class,
                () -> LoteService.resolverFechas(
                        VENCE_DICIEMBRE, null, LocalDate.of(2026, 10, 1), RETIRO_NOVIEMBRE, null));
    }
}
