package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.ControlStockNegativo;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El stock actual de un registro del control es el stock de HOY del producto en la sucursal del
 * registro, no el stock previo guardado. Sin producto o sin sucursal no hay numero que mostrar.
 */
class ControlStockNegativoResolverTest {

    private MovimientoStockService movimientoStockService;
    private ControlStockNegativoResolver resolver;

    @BeforeEach
    void setUp() {
        movimientoStockService = mock(MovimientoStockService.class);
        resolver = new ControlStockNegativoResolver();
        ReflectionTestUtils.setField(resolver, "sucursalService", mock(SucursalService.class));
        ReflectionTestUtils.setField(resolver, "movimientoStockService", movimientoStockService);
    }

    private ControlStockNegativo registro(Long productoId, Long sucursalId) {
        ControlStockNegativo c = new ControlStockNegativo();
        if (productoId != null) {
            Producto p = new Producto();
            p.setId(productoId);
            c.setProducto(p);
        }
        c.setSucursalId(sucursalId);
        c.setStockPrevio(-4D);
        return c;
    }

    @Test
    void devuelveElStockDeHoyDeEseProductoEnEsaSucursal() {
        when(movimientoStockService.stockByProductoIdAndSucursalId(77L, 6L)).thenReturn(12D);

        assertEquals(12D, resolver.stockActual(registro(77L, 6L)));
    }

    @Test
    void sinProductoOSinSucursalNoHayNumero() {
        assertNull(resolver.stockActual(registro(null, 6L)));
        assertNull(resolver.stockActual(registro(77L, null)));
        verify(movimientoStockService, never()).stockByProductoIdAndSucursalId(any(), any());
    }
}
