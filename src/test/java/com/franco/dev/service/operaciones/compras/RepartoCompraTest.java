package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.graphql.operaciones.compras.RenglonFacturaMobile;
import com.franco.dev.graphql.operaciones.compras.RepartoRenglonMobileInput;
import com.franco.dev.graphql.operaciones.compras.VariacionCostoPedidoItem;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RepartoCompraTest {

    private static DocumentoRecibidoItem renglon(long id, double cantidad) {
        DocumentoRecibidoItem r = new DocumentoRecibidoItem();
        r.setId(id);
        r.setOrden((int) id);
        r.setDescripcion("PRODUCTO " + id);
        r.setCantidad(cantidad);
        return r;
    }

    private static RenglonFacturaMobile cruce(long id, double cantidadBase) {
        RenglonFacturaMobile r = new RenglonFacturaMobile();
        r.setId(id);
        r.setCantidadBase(cantidadBase);
        return r;
    }

    private static RepartoRenglonMobileInput parte(long renglon, long sucursal, double cantidad) {
        RepartoRenglonMobileInput p = new RepartoRenglonMobileInput();
        p.setRenglonId(renglon);
        p.setSucursalId(sucursal);
        p.setCantidad(cantidad);
        return p;
    }

    @Test
    void conUnaSucursalVaTodoAElla() {
        Map<Long, double[]> r = ComprasMobileService.repartoPorRenglon(
                Collections.singletonList(renglon(1, 14)), Collections.singletonMap(1L, cruce(1, 168)),
                Collections.singletonList(13L), null);
        assertArrayEquals(new double[]{168}, r.get(1L), 1e-9);
    }

    @Test
    void conVariasSePasaDeCajasAUnidades() {
        // 14 cajas de 12: 5, 5 y 4 cajas son 60, 60 y 48 unidades
        Map<Long, double[]> r = ComprasMobileService.repartoPorRenglon(
                Collections.singletonList(renglon(1, 14)), Collections.singletonMap(1L, cruce(1, 168)),
                Arrays.asList(13L, 2L, 5L), Arrays.asList(parte(1, 13, 5), parte(1, 2, 5), parte(1, 5, 4)));
        assertArrayEquals(new double[]{60, 60, 48}, r.get(1L), 1e-9);
    }

    @Test
    void unaSucursalPuedeQuedarSinNada() {
        Map<Long, double[]> r = ComprasMobileService.repartoPorRenglon(
                Collections.singletonList(renglon(1, 10)), Collections.singletonMap(1L, cruce(1, 10)),
                Arrays.asList(13L, 2L), Collections.singletonList(parte(1, 13, 10)));
        assertArrayEquals(new double[]{10, 0}, r.get(1L), 1e-9);
    }

    @Test
    void elRepartoTieneQueSumarElRenglon() {
        GraphQLException e = assertThrows(GraphQLException.class, () -> ComprasMobileService.repartoPorRenglon(
                Collections.singletonList(renglon(1, 14)), Collections.singletonMap(1L, cruce(1, 168)),
                Arrays.asList(13L, 2L), Arrays.asList(parte(1, 13, 5), parte(1, 2, 5))));
        assertTrue(e.getMessage().contains("#1"), e.getMessage());
    }

    @Test
    void unRenglonSinRepartoNoPasa() {
        assertThrows(GraphQLException.class, () -> ComprasMobileService.repartoPorRenglon(
                Arrays.asList(renglon(1, 2), renglon(2, 3)), mapa(cruce(1, 2), cruce(2, 3)),
                Arrays.asList(13L, 2L), Arrays.asList(parte(1, 13, 1), parte(1, 2, 1))));
    }

    @Test
    void noSeRepartePorUnaSucursalNoElegida() {
        assertThrows(GraphQLException.class, () -> ComprasMobileService.repartoPorRenglon(
                Collections.singletonList(renglon(1, 2)), Collections.singletonMap(1L, cruce(1, 2)),
                Arrays.asList(13L, 2L), Arrays.asList(parte(1, 13, 1), parte(1, 99, 1))));
    }

    private static Map<Long, RenglonFacturaMobile> mapa(RenglonFacturaMobile... rs) {
        Map<Long, RenglonFacturaMobile> m = new HashMap<>();
        for (RenglonFacturaMobile r : rs) m.put(r.getId(), r);
        return m;
    }

    // ------------------------------------------------------------------ variación de costo

    @Test
    void subioBajoEIgual() {
        VariacionCostoPedidoItem subio = VariacionCostoService.variacion(1L, 1350.0, 1200.0, false);
        assertEquals("SUBIO", subio.getDireccion());
        assertEquals("Subió contra la última compra: 1.200 → 1.350 (+12,5 %)", subio.getTexto());
        assertEquals("BAJO", VariacionCostoService.variacion(1L, 1000.0, 1200.0, false).getDireccion());
        VariacionCostoPedidoItem igual = VariacionCostoService.variacion(1L, 1205.0, 1200.0, false);
        assertEquals("IGUAL", igual.getDireccion());
        assertNull(igual.getTexto());
    }

    @Test
    void sinCompraAnteriorOBonificacionNoSeCompara() {
        assertEquals("SIN_HISTORIAL", VariacionCostoService.variacion(1L, 1000.0, null, false).getDireccion());
        assertEquals("SIN_HISTORIAL", VariacionCostoService.variacion(1L, 1000.0, 500.0, true).getDireccion());
    }
}
