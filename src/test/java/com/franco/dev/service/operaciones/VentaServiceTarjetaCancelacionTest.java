package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Que le pasa a la venta con tarjeta cuando su venta se cancela o se reactiva.
 *
 * <p>Hasta el 2026-09-28 {@code VentaService.cancelarVenta} no tocaba {@code venta_tarjeta}: el
 * cupon de una venta cancelada seguia bloqueado y un PENDIENTE seguia reclamando el cierre de caja.
 */
class VentaServiceTarjetaCancelacionTest {

    private static VentaTarjeta vt(String estado) {
        VentaTarjeta v = new VentaTarjeta();
        v.setEstado(estado);
        return v;
    }

    @Test
    void cancelarLaVentaCancelaLaTarjetaAunqueEsteCompletada() {
        assertEquals("CANCELADO", VentaService.estadoTarjetaParaVenta(vt("COMPLETADO"), VentaEstado.CANCELADA));
        assertEquals("CANCELADO", VentaService.estadoTarjetaParaVenta(vt("PENDIENTE"), VentaEstado.CANCELADA));
        assertEquals("CANCELADO", VentaService.estadoTarjetaParaVenta(vt("NO_COMPLETADO"), VentaEstado.CANCELADA));
    }

    @Test
    void reactivarConDatosDelCuponVuelveACompletado() {
        VentaTarjeta v = vt("CANCELADO");
        v.setCodigoAutorizacion("607041");
        assertEquals("COMPLETADO", VentaService.estadoTarjetaParaVenta(v, VentaEstado.CONCLUIDA));

        VentaTarjeta m = vt("CANCELADO");
        m.setMontoEscaneado(new BigDecimal("9000"));
        assertEquals("COMPLETADO", VentaService.estadoTarjetaParaVenta(m, VentaEstado.CONCLUIDA));
    }

    @Test
    void reactivarSinCuponPeroMarcadaSinConciliarVuelveANoCompletado() {
        VentaTarjeta v = vt("CANCELADO");
        v.setNoCompletadoEn(LocalDateTime.of(2026, 9, 27, 18, 8));
        assertEquals("NO_COMPLETADO", VentaService.estadoTarjetaParaVenta(v, VentaEstado.CONCLUIDA));
    }

    @Test
    void reactivarSinNadaVuelveAPendiente() {
        VentaTarjeta v = vt("CANCELADO");
        v.setCodigoAutorizacion("  ");
        assertEquals("PENDIENTE", VentaService.estadoTarjetaParaVenta(v, VentaEstado.CONCLUIDA));
    }

    @Test
    void reactivarNoTocaUnaTarjetaQueNoEstabaCancelada() {
        assertEquals("COMPLETADO", VentaService.estadoTarjetaParaVenta(vt("COMPLETADO"), VentaEstado.CONCLUIDA));
    }

    // ---------------------------------------------------------------- #195: cupon usado en otra venta

    /** Mientras la venta estuvo cancelada otra uso su cupon: revivirlo dejaba dos COMPLETADOS iguales. */
    @Test
    void reactivarConCuponYaCompletadoEnOtraVuelveAPendiente() {
        VentaTarjeta v = vt("CANCELADO");
        v.setQrCrudo("FRCP1*U1**BRL*50*REF*202611211200");
        assertEquals("PENDIENTE", VentaService.estadoTarjetaParaVenta(v, VentaEstado.CONCLUIDA, () -> true));
    }

    @Test
    void reactivarConCuponLibreVuelveACompletado() {
        VentaTarjeta v = vt("CANCELADO");
        v.setCodigoAutorizacion("607041");
        assertEquals("COMPLETADO", VentaService.estadoTarjetaParaVenta(v, VentaEstado.CONCLUIDA, () -> false));
    }

    /** Cancelar no consulta nada: el cupon queda libre igual. */
    @Test
    void cancelarNoPreguntaSiElCuponEstaUsado() {
        VentaTarjeta v = vt("COMPLETADO");
        v.setQrCrudo("QR");
        assertEquals("CANCELADO", VentaService.estadoTarjetaParaVenta(v, VentaEstado.CANCELADA, () -> {
            throw new AssertionError("no tenia que consultar");
        }));
    }

    /** Sin datos de cupon no hay nada que revivir: no se consulta. */
    @Test
    void reactivarSinCuponNoPreguntaSiElCuponEstaUsado() {
        assertEquals("PENDIENTE", VentaService.estadoTarjetaParaVenta(vt("CANCELADO"), VentaEstado.CONCLUIDA, () -> {
            throw new AssertionError("no tenia que consultar");
        }));
    }

    @Test
    void reactivarUnaTarjetaQueNoEstabaCanceladaNoPregunta() {
        assertEquals("COMPLETADO", VentaService.estadoTarjetaParaVenta(vt("COMPLETADO"), VentaEstado.CONCLUIDA, () -> {
            throw new AssertionError("no tenia que consultar");
        }));
    }
}
