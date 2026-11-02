package com.franco.dev.service.operaciones.compras;

import com.franco.dev.graphql.operaciones.compras.CompraPedidoItemMobile;
import com.franco.dev.graphql.operaciones.compras.ResumenFinalizarNotasMobile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cuándo la factura cargada sola al finalizar la planificación cierra también la recepción documental. */
class ConciliacionAutomaticaTest {

    private ResumenFinalizarNotasMobile completo() {
        ResumenFinalizarNotasMobile r = new ResumenFinalizarNotasMobile();
        r.setPuedeFinalizar(true);
        r.setCantidadNotas(1);
        r.setNotasPendientes(0);
        r.setItemsSinDistribuir(0);
        return r;
    }

    @Test
    void todoConciliadoSeFinalizaSolo() {
        assertTrue(ComprasMobileService.conciliacionSinPendientes(completo()));
    }

    @Test
    void conUnaNotaPendienteQuedaAMano() {
        ResumenFinalizarNotasMobile r = completo();
        r.setNotasPendientes(1);
        assertFalse(ComprasMobileService.conciliacionSinPendientes(r));
    }

    @Test
    void conItemsSinFacturarQuedaAMano() {
        ResumenFinalizarNotasMobile r = completo();
        r.getItemsSinFacturar().add(new CompraPedidoItemMobile());
        assertFalse(ComprasMobileService.conciliacionSinPendientes(r));
    }

    @Test
    void conItemsSinDistribuirQuedaAMano() {
        ResumenFinalizarNotasMobile r = completo();
        r.setItemsSinDistribuir(2);
        assertFalse(ComprasMobileService.conciliacionSinPendientes(r));
    }

    @Test
    void conLaEtapaCerradaOSinNotasNoHaceNada() {
        ResumenFinalizarNotasMobile cerrada = completo();
        cerrada.setPuedeFinalizar(false);
        assertFalse(ComprasMobileService.conciliacionSinPendientes(cerrada));
        ResumenFinalizarNotasMobile sinNotas = completo();
        sinNotas.setCantidadNotas(0);
        assertFalse(ComprasMobileService.conciliacionSinPendientes(sinNotas));
    }
}
