package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionConfiguracion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoDevolucion;
import com.franco.dev.domain.operaciones.enums.TipoResolucionDevolucion;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.NotaCreditoDevolucionItemInput;
import com.franco.dev.repository.operaciones.DevolucionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issue #208: resolucion y monto_acreditado quedaban NULL segun el desenlace.
 * Reproducido el 2026-10-31 en local: descarte de la devolucion 24 sin resolucion
 * y nota TEST-208 del retiro 7 con la devolucion 3 sin monto.
 */
class DevolucionResolucionYMontoTest {

    private static final double DELTA = 0.0001;

    // ===================== descarte =====================

    @Test
    void descartar_dejaResolucionDescarte() {
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DevolucionItemService itemService = mock(DevolucionItemService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        DevolucionConfiguracionService configService = mock(DevolucionConfiguracionService.class);
        when(configService.getConfiguracion()).thenReturn(new DevolucionConfiguracion());
        when(ctx.getBean(DevolucionConfiguracionService.class)).thenReturn(configService);
        DevolucionService service = new DevolucionService(repo, itemService, null, null,
                null, null, null, null, null, ctx);

        Sucursal origen = new Sucursal();
        origen.setId(1L);
        Devolucion d = new Devolucion();
        d.setId(1L);
        d.setTipo(TipoDevolucion.SIN_PROVEEDOR);
        d.setEstado(DevolucionEstado.SEPARADO);
        d.setSucursalOrigen(origen);
        when(repo.findById(1L)).thenReturn(Optional.of(d));
        // Sin items con costo no se genera gasto: aca solo importa la resolucion.
        when(itemService.findByDevolucionId(1L)).thenReturn(Collections.emptyList());

        Devolucion r = service.avanzarEstado(1L, DevolucionEstado.DESCARTADO, null);

        assertEquals(DevolucionEstado.DESCARTADO, r.getEstado());
        assertEquals(TipoResolucionDevolucion.DESCARTE, r.getResolucion());
        assertTrue(r.getFinalizado());
    }

    // ===================== reparto del monto de la nota =====================

    private static DevolucionItem item(long productoId, double cantidad, double factor) {
        Producto p = new Producto();
        p.setId(productoId);
        Presentacion pres = new Presentacion();
        pres.setCantidad(factor);
        DevolucionItem i = new DevolucionItem();
        i.setProducto(p);
        i.setPresentacion(pres);
        i.setCantidad(cantidad);
        return i;
    }

    private static NotaCreditoDevolucionItemInput linea(long productoId, double cantidadBase, double total) {
        NotaCreditoDevolucionItemInput l = new NotaCreditoDevolucionItemInput();
        l.setProductoId(productoId);
        l.setCantidadBase(cantidadBase);
        l.setTotal(total);
        return l;
    }

    private static double suma(Map<Long, Double> montos) {
        return montos.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    @Test
    void unaSolaDevolucion_seLlevaElTotalDeLaNota() {
        // El caso reproducido: 1 caja x6 de CORONITA, nota de 33.091,3663.
        Map<Long, List<DevolucionItem>> items = new LinkedHashMap<>();
        items.put(3L, Collections.singletonList(item(32L, 1.0, 6.0)));

        Map<Long, Double> montos = NotaCreditoDevolucionService.repartirMonto(items,
                Collections.singletonList(linea(32L, 6.0, 33091.3663)), 33091.3663);

        assertEquals(33091.3663, montos.get(3L), DELTA);
    }

    @Test
    void variasDevoluciones_repartePorValorAlCostoDeLaNota() {
        // Producto 32 a 1.000/unidad, producto 40 a 500/unidad.
        Map<Long, List<DevolucionItem>> items = new LinkedHashMap<>();
        items.put(1L, Arrays.asList(item(32L, 1.0, 6.0), item(40L, 2.0, 1.0))); // 6.000 + 1.000
        items.put(2L, Collections.singletonList(item(32L, 3.0, 1.0)));          // 3.000
        List<NotaCreditoDevolucionItemInput> nota = Arrays.asList(
                linea(32L, 9.0, 9000.0), linea(40L, 2.0, 1000.0));

        Map<Long, Double> montos = NotaCreditoDevolucionService.repartirMonto(items, nota, 10000.0);

        assertEquals(7000.0, montos.get(1L), DELTA);
        assertEquals(3000.0, montos.get(2L), DELTA);
    }

    @Test
    void notaConCostoEditado_repartePorProporcionYSumaExacto() {
        // El operador bajo el costo en la nota: 3 devoluciones iguales, total no divisible.
        Map<Long, List<DevolucionItem>> items = new LinkedHashMap<>();
        items.put(1L, Collections.singletonList(item(32L, 1.0, 1.0)));
        items.put(2L, Collections.singletonList(item(32L, 1.0, 1.0)));
        items.put(3L, Collections.singletonList(item(32L, 1.0, 1.0)));

        Map<Long, Double> montos = NotaCreditoDevolucionService.repartirMonto(items,
                Collections.singletonList(linea(32L, 3.0, 1000.0)), 1000.0);

        assertEquals(1000.0 / 3, montos.get(1L), DELTA);
        assertEquals(1000.0 / 3, montos.get(2L), DELTA);
        assertEquals(1000.0, suma(montos), 1e-9);
    }

    @Test
    void productosFueraDeLaNota_repartePorCantidadBase() {
        Map<Long, List<DevolucionItem>> items = new LinkedHashMap<>();
        items.put(1L, Collections.singletonList(item(32L, 1.0, 6.0))); // 6 unidades
        items.put(2L, Collections.singletonList(item(40L, 2.0, 1.0))); // 2 unidades

        Map<Long, Double> montos = NotaCreditoDevolucionService.repartirMonto(items,
                Collections.singletonList(linea(99L, 1.0, 800.0)), 800.0);

        assertEquals(600.0, montos.get(1L), DELTA);
        assertEquals(200.0, montos.get(2L), DELTA);
    }

    @Test
    void devolucionesSinItems_reparteEnPartesIguales() {
        Map<Long, List<DevolucionItem>> items = new LinkedHashMap<>();
        items.put(1L, Collections.emptyList());
        items.put(2L, Collections.emptyList());

        Map<Long, Double> montos = NotaCreditoDevolucionService.repartirMonto(items,
                Collections.singletonList(linea(32L, 1.0, 500.0)), 500.0);

        assertEquals(250.0, montos.get(1L), DELTA);
        assertEquals(250.0, montos.get(2L), DELTA);
    }
}
