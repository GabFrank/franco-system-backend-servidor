package com.franco.dev.service.productos;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.productos.CostosPorProductoRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TABLA DE ORO (golden tests) del costo por producto del central.
 *
 * <p>Fija el comportamiento ACTUAL de {@link CostosPorProductoService#aplicarCostoCompra} y
 * {@link CostosPorProductoService#registrarCostoCompraManual}: para cada escenario se captura la fila
 * de {@code costo_por_producto} que el servicio guarda (con {@link ArgumentCaptor}) y se comparan los
 * valores exactos de las columnas existentes — {@code costo_medio}, {@code ultimo_precio_compra},
 * {@code cotizacion}, {@code moneda_id}, {@code existencia} — y si hubo o no un {@code save}.</p>
 *
 * <p><b>NO EDITAR ESTA TABLA durante el refactor de «moneda por módulo» (Compras y costos).</b> Ese
 * refactor agrega columnas nuevas (p.ej. {@code costo_medio_costeo}) en la moneda de costeo
 * configurable, pero las columnas existentes deben seguir guardando EXACTAMENTE los mismos valores en
 * guaraníes que hoy (empresa «bodega»). Si un escenario de acá se pone en rojo, el refactor cambió el
 * costo histórico: es el refactor el que está mal, no la tabla. Agregar escenarios nuevos sí está
 * permitido; modificar o borrar los existentes, no.</p>
 *
 * <p>Comportamientos fijados que pueden sorprender (son los de hoy, a propósito):</p>
 * <ul>
 *   <li>Con cotización {@code null}, {@code 1} o {@code < 1} no se convierte: el costo extranjero se
 *       guarda tal cual como si fuera Gs, y {@code cotizacion} queda en 1.0 (si era null) o en la
 *       recibida.</li>
 *   <li>{@code existencia} nunca la escribe el servicio: queda {@code null}.</li>
 *   <li>La vía manual siempre guarda {@code cotizacion = 1.0}, con la moneda que reciba.</li>
 * </ul>
 */
class CostoGoldenTest {

    private static final long GS = 1L;
    private static final long USD = 2L;
    private static final double DELTA = 1e-9;
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 27, 10, 0);

    private enum Via { COMPRA, MANUAL }

    private enum Resultado {
        /** Se insertó una fila nueva (save). */
        GUARDA,
        /** No se guardó nada y se devolvió el último costo existente (dedup o guard). */
        DEVUELVE_ANTERIOR,
        /** No se guardó nada y se devolvió null (entrada inválida). */
        NULO
    }

    /** Fila de la tabla de oro. */
    static final class Caso {
        final String nombre;
        final Via via;
        // estado previo
        final Double antCostoMedio;
        final Double antUltimoPrecio;
        final Long antMonedaId;
        final Double stockReal;          // stock real devuelto por MovimientoStockService (incluye la entrada)
        // entrada
        final Double cantidad;
        final Double costoUnitario;
        final Long monedaId;
        final Double cotizacion;
        // esperado
        final Resultado resultado;
        final Double espCostoMedio;
        final Double espUltimoPrecio;
        final Double espCotizacion;
        final Long espMonedaId;

        Caso(String nombre, Via via, Double antCostoMedio, Double antUltimoPrecio, Long antMonedaId,
             Double stockReal, Double cantidad, Double costoUnitario, Long monedaId, Double cotizacion,
             Resultado resultado, Double espCostoMedio, Double espUltimoPrecio, Double espCotizacion,
             Long espMonedaId) {
            this.nombre = nombre;
            this.via = via;
            this.antCostoMedio = antCostoMedio;
            this.antUltimoPrecio = antUltimoPrecio;
            this.antMonedaId = antMonedaId;
            this.stockReal = stockReal;
            this.cantidad = cantidad;
            this.costoUnitario = costoUnitario;
            this.monedaId = monedaId;
            this.cotizacion = cotizacion;
            this.resultado = resultado;
            this.espCostoMedio = espCostoMedio;
            this.espUltimoPrecio = espUltimoPrecio;
            this.espCotizacion = espCotizacion;
            this.espMonedaId = espMonedaId;
        }

        boolean tieneAnterior() {
            return antCostoMedio != null || antUltimoPrecio != null || antMonedaId != null;
        }

        @Override
        public String toString() {
            return nombre;
        }
    }

    private static Caso compra(String nombre, Double antCm, Double antUpc, Long antMon, Double stock,
                               Double cant, Double costo, Long mon, Double cotiz,
                               Resultado r, Double cm, Double upc, Double espCotiz, Long espMon) {
        return new Caso(nombre, Via.COMPRA, antCm, antUpc, antMon, stock, cant, costo, mon, cotiz,
                r, cm, upc, espCotiz, espMon);
    }

    private static Caso manual(String nombre, Double antCm, Double antUpc, Long antMon,
                               Double precio, Long mon,
                               Resultado r, Double cm, Double upc, Double espCotiz, Long espMon) {
        return new Caso(nombre, Via.MANUAL, antCm, antUpc, antMon, null, null, precio, mon, null,
                r, cm, upc, espCotiz, espMon);
    }

    /** LA TABLA DE ORO. Valores obtenidos corriendo el código actual (2026-10-27). No editar. */
    static Stream<Caso> tabla() {
        final Resultado G = Resultado.GUARDA;
        final Resultado A = Resultado.DEVUELVE_ANTERIOR;
        final Resultado N = Resultado.NULO;
        return Stream.of(
                // 1. Primera compra en PYG (sin costo previo)
                compra("01 primera compra PYG", null, null, null, 10.0,
                        10.0, 5000.0, GS, 1.0, G, 5000.0, 5000.0, 1.0, GS),
                // 2. PYG con costo previo y stock: (20*4000 + 10*7000) / 30 = 5000
                compra("02 PYG ponderado", 4000.0, 4000.0, GS, 30.0,
                        10.0, 7000.0, GS, 1.0, G, 5000.0, 7000.0, 1.0, GS),
                compra("02b PYG ponderado con decimales", 100.0, 100.0, GS, 15.0,
                        10.0, 120.0, GS, 1.0, G, 1700.0 / 15.0, 120.0, 1.0, GS),
                // 3. USD con cotización 5700: 10 USD → 57000 Gs; (10*40000 + 10*57000) / 20 = 48500
                compra("03 USD cotizacion 5700 ponderado", 40000.0, 40000.0, GS, 20.0,
                        10.0, 10.0, USD, 5700.0, G, 48500.0, 57000.0, 5700.0, USD),
                compra("03b USD cotizacion 5700 primera compra", null, null, null, 5.0,
                        5.0, 2.5, USD, 5700.0, G, 14250.0, 14250.0, 5700.0, USD),
                // 4. Moneda extranjera sin conversión (cotización null, 1, 0.5): se guarda tal cual
                compra("04a USD cotizacion null (sin conversion, cotiz=1.0)", null, null, null, 10.0,
                        10.0, 10.0, USD, null, G, 10.0, 10.0, 1.0, USD),
                compra("04b USD cotizacion 1 (sin conversion)", null, null, null, 10.0,
                        10.0, 10.0, USD, 1.0, G, 10.0, 10.0, 1.0, USD),
                compra("04c USD cotizacion 0.5 (sin conversion, guarda 0.5)", null, null, null, 10.0,
                        10.0, 10.0, USD, 0.5, G, 10.0, 10.0, 0.5, USD),
                // 5. Stock previo cero / negativo / desconocido: no pondera, resetea al costo de la compra
                compra("05a stock previo cero", 5000.0, 5000.0, GS, 10.0,
                        10.0, 6000.0, GS, 1.0, G, 6000.0, 6000.0, 1.0, GS),
                compra("05b stock previo negativo", 5000.0, 5000.0, GS, 5.0,
                        10.0, 6000.0, GS, 1.0, G, 6000.0, 6000.0, 1.0, GS),
                compra("05c stock real null (tratado como 0)", 5000.0, 5000.0, GS, null,
                        10.0, 6000.0, GS, 1.0, G, 6000.0, 6000.0, 1.0, GS),
                compra("05d costo medio previo 0 (no pondera)", 0.0, 5000.0, GS, 30.0,
                        10.0, 6000.0, GS, 1.0, G, 6000.0, 6000.0, 1.0, GS),
                // 6. Guard de salto sospechoso (factor 100) y ancla mínima (100 Gs)
                compra("06a guard: salto > 100x se descarta", 1000.0, 1000.0, GS, 20.0,
                        10.0, 100001.0, GS, 1.0, A, null, null, null, null),
                compra("06b guard: doble cotizacion 57000*5700 se descarta", 57000.0, 57000.0, USD, 20.0,
                        10.0, 57000.0, USD, 5700.0, A, null, null, null, null),
                compra("06c guard: exactamente 100x no dispara", 1000.0, 1000.0, GS, 10.0,
                        10.0, 100000.0, GS, 1.0, G, 100000.0, 100000.0, 1.0, GS),
                compra("06d ancla < 100 Gs no dispara el guard", 7.99, 7.99, USD, 20.0,
                        10.0, 10.0, USD, 5700.0, G, (10 * 7.99 + 10 * 57000.0) / 20.0, 57000.0, 5700.0, USD),
                compra("06e ancla exactamente 100 Gs si dispara", 100.0, 100.0, GS, 20.0,
                        10.0, 10001.0, GS, 1.0, A, null, null, null, null),
                compra("06f baja de precio grande se acepta", 100000.0, 100000.0, GS, 10.0,
                        10.0, 10.0, GS, 1.0, G, 10.0, 10.0, 1.0, GS),
                // 7. Dedup
                compra("07a dedup: mismos valores no guarda", 5000.0, 5000.0, GS, 20.0,
                        10.0, 5000.0, GS, 1.0, A, null, null, null, null),
                compra("07b dedup: diferencia < 0.01 no guarda", 5000.005, 5000.0, GS, 20.0,
                        10.0, 5000.0, GS, 1.0, A, null, null, null, null),
                compra("07c dedup: misma cifra, otra moneda si guarda", 5000.0, 5000.0, GS, 20.0,
                        10.0, 5000.0, USD, 1.0, G, 5000.0, 5000.0, 1.0, USD),
                // Entradas inválidas: no guarda, devuelve null
                compra("00a costo cero no guarda", 5000.0, 5000.0, GS, 20.0,
                        10.0, 0.0, GS, 1.0, N, null, null, null, null),
                compra("00b costo null no guarda", 5000.0, 5000.0, GS, 20.0,
                        10.0, null, GS, 1.0, N, null, null, null, null),
                compra("00c cantidad cero no guarda", 5000.0, 5000.0, GS, 20.0,
                        0.0, 6000.0, GS, 1.0, N, null, null, null, null),
                // 8. Registro manual (transferencia desde COMPRAS): no pondera, cotización siempre 1.0
                manual("08a manual sin costo previo", null, null, null,
                        8000.0, GS, G, 8000.0, 8000.0, 1.0, GS),
                manual("08b manual reemplaza sin ponderar", 4000.0, 4000.0, GS,
                        8000.0, GS, G, 8000.0, 8000.0, 1.0, GS),
                manual("08c manual dedup no guarda", 8000.0, 8000.0, GS,
                        8000.0, GS, A, null, null, null, null),
                manual("08d manual con moneda USD guarda cotiz 1.0", null, null, null,
                        10.0, USD, G, 10.0, 10.0, 1.0, USD),
                manual("08e manual precio cero no guarda", 4000.0, 4000.0, GS,
                        0.0, GS, N, null, null, null, null)
        );
    }

    private CostosPorProductoRepository repository;
    private MovimientoStockService movimientoStockService;
    private CostosPorProductoService service;

    @BeforeEach
    void setUp() {
        repository = mock(CostosPorProductoRepository.class);
        movimientoStockService = mock(MovimientoStockService.class);
        ModificacionService modificacionService = mock(ModificacionService.class);
        service = new CostosPorProductoService(repository, movimientoStockService, modificacionService);
        when(repository.save(any(CostoPorProducto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Moneda moneda(Long id) {
        if (id == null) return null;
        Moneda m = new Moneda();
        m.setId(id);
        return m;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tabla")
    void tablaDeOro(Caso c) {
        Producto producto = new Producto();
        producto.setId(10L);
        Sucursal sucursal = new Sucursal();
        sucursal.setId(2L);

        CostoPorProducto anterior = null;
        if (c.tieneAnterior()) {
            anterior = new CostoPorProducto();
            anterior.setId(1L);
            anterior.setCostoMedio(c.antCostoMedio);
            anterior.setUltimoPrecioCompra(c.antUltimoPrecio);
            anterior.setMoneda(moneda(c.antMonedaId));
        }
        List<CostoPorProducto> ultimo = anterior != null
                ? Collections.singletonList(anterior) : Collections.emptyList();
        when(repository.findLastByProductoId(anyLong(), any())).thenReturn(ultimo);
        when(movimientoStockService.stockByProductoIdExcluyendoNombresSucursal(anyLong(), any()))
                .thenReturn(c.stockReal);

        CostoPorProducto r;
        if (c.via == Via.COMPRA) {
            r = service.aplicarCostoCompra(producto, c.cantidad, c.costoUnitario, moneda(c.monedaId),
                    c.cotizacion, sucursal, null, FECHA);
        } else {
            r = service.registrarCostoCompraManual(producto, c.costoUnitario, moneda(c.monedaId),
                    sucursal, null, FECHA);
        }

        switch (c.resultado) {
            case NULO:
                assertNull(r, "debía devolver null");
                verify(repository, never()).save(any(CostoPorProducto.class));
                break;
            case DEVUELVE_ANTERIOR:
                assertSame(anterior, r, "debía devolver el último costo sin guardar");
                verify(repository, never()).save(any(CostoPorProducto.class));
                break;
            case GUARDA:
                ArgumentCaptor<CostoPorProducto> captor = ArgumentCaptor.forClass(CostoPorProducto.class);
                verify(repository, times(1)).save(captor.capture());
                CostoPorProducto g = captor.getValue();
                assertSame(g, r, "debía devolver la fila guardada");
                assertNull(g.getId(), "fila nueva (insert), no update");
                assertEquals(c.espCostoMedio, g.getCostoMedio(), DELTA, "costo_medio");
                assertEquals(c.espUltimoPrecio, g.getUltimoPrecioCompra(), DELTA, "ultimo_precio_compra");
                assertEquals(c.espCotizacion, g.getCotizacion(), DELTA, "cotizacion");
                assertEquals(c.espMonedaId, g.getMoneda() != null ? g.getMoneda().getId() : null, "moneda_id");
                assertNull(g.getExistencia(), "existencia (el servicio no la escribe)");
                assertNull(g.getUltimoPrecioVenta(), "ultimo_precio_venta (el servicio no la escribe)");
                assertNull(g.getMovimientoStock(), "movimiento_stock (el servicio no lo escribe)");
                assertSame(producto, g.getProducto(), "producto");
                assertSame(sucursal, g.getSucursal(), "sucursal");
                assertEquals(FECHA, g.getCreadoEn(), "creado_en");
                break;
            default:
                throw new IllegalStateException();
        }

        if (c.via == Via.COMPRA && c.resultado != Resultado.NULO) {
            // El stock que pondera excluye siempre la pseudo-sucursal COMPRAS.
            verify(movimientoStockService).stockByProductoIdExcluyendoNombresSucursal(10L,
                    Arrays.asList(CostosPorProductoService.SUCURSAL_COMPRAS));
        }
    }
}
