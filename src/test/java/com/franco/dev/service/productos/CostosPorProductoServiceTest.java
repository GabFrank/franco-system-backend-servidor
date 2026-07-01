package com.franco.dev.service.productos;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.productos.CostosPorProductoRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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
 * Tests de la capa de servicio del costo medio: guards, dedup, exclusión de la pseudo-sucursal
 * COMPRAS del stock y conversión a Gs. La aritmética pura ya la cubre {@code CostoMedioCalculatorTest};
 * acá se verifica el wiring (findLast → calcular → dedup → save) con mocks.
 */
class CostosPorProductoServiceTest {

    private static final double DELTA = 0.001;

    private CostosPorProductoRepository repository;
    private MovimientoStockService movimientoStockService;
    private ModificacionService modificacionService;
    private CostosPorProductoService service;

    @BeforeEach
    void setUp() {
        repository = mock(CostosPorProductoRepository.class);
        movimientoStockService = mock(MovimientoStockService.class);
        modificacionService = mock(ModificacionService.class);
        // @AllArgsConstructor (orden de declaración): repository, movimientoStockService, modificacionService
        service = new CostosPorProductoService(repository, movimientoStockService, modificacionService);
        // super.save() → getRepository().save(): devuelve el mismo objeto para poder inspeccionarlo
        when(repository.save(any(CostoPorProducto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- helpers ---

    private Producto producto(long id) {
        Producto p = new Producto();
        p.setId(id);
        return p;
    }

    private Moneda moneda(long id) {
        Moneda m = new Moneda();
        m.setId(id);
        return m;
    }

    private Sucursal sucursal(long id) {
        Sucursal s = new Sucursal();
        s.setId(id);
        return s;
    }

    private CostoPorProducto costoAnterior(Double costoMedio, Double ultimoPrecioCompra, Moneda moneda) {
        CostoPorProducto c = new CostoPorProducto();
        c.setId(1L);
        c.setCostoMedio(costoMedio);
        c.setUltimoPrecioCompra(ultimoPrecioCompra);
        c.setMoneda(moneda);
        return c;
    }

    private void stubUltimoCosto(CostoPorProducto anterior) {
        List<CostoPorProducto> list = anterior != null
                ? Collections.singletonList(anterior) : Collections.emptyList();
        when(repository.findLastByProductoId(anyLong(), any())).thenReturn(list);
    }

    private void stubStockReal(double stock) {
        when(movimientoStockService.stockByProductoIdExcluyendoNombresSucursal(anyLong(), any()))
                .thenReturn(stock);
    }

    // --- aplicarCostoCompra: ponderación con base válida ---

    @Test
    void aplicarCostoCompra_stockPositivoConCostoPrevio_ponderaYGuarda() {
        Moneda gs = moneda(1L);
        stubUltimoCosto(costoAnterior(100.0, 100.0, gs));
        stubStockReal(15.0); // stock anterior = 15 - 10 = 5

        CostoPorProducto r = service.aplicarCostoCompra(producto(10L), 10.0, 120.0, gs, 1.0,
                sucursal(2L), null, LocalDateTime.now());

        // (5*100 + 10*120) / 15 = 113.333
        assertEquals(1700.0 / 15.0, r.getCostoMedio(), DELTA);
        assertEquals(120.0, r.getUltimoPrecioCompra(), DELTA); // moneda original, sin convertir
        verify(repository, times(1)).save(any(CostoPorProducto.class));
    }

    @Test
    void aplicarCostoCompra_monedaExtranjera_ponderaEnGuaranies() {
        Moneda usd = moneda(2L);
        stubUltimoCosto(costoAnterior(100000.0, 90000.0, usd));
        stubStockReal(15.0); // anterior 5

        // USD 10 a 7200 → 72000 Gs
        CostoPorProducto r = service.aplicarCostoCompra(producto(10L), 10.0, 10.0, usd, 7200.0,
                sucursal(2L), null, LocalDateTime.now());

        // (5*100000 + 10*72000) / 15 = 1_220_000 / 15
        assertEquals(1_220_000.0 / 15.0, r.getCostoMedio(), DELTA);
        assertEquals(10.0, r.getUltimoPrecioCompra(), DELTA); // en moneda original
        verify(repository, times(1)).save(any(CostoPorProducto.class));
    }

    @Test
    void aplicarCostoCompra_stockAnteriorNoPositivo_reseteaAlCostoCompra() {
        Moneda gs = moneda(1L);
        stubUltimoCosto(costoAnterior(100.0, 100.0, gs));
        stubStockReal(5.0); // anterior = 5 - 10 = -5 → no pondera

        CostoPorProducto r = service.aplicarCostoCompra(producto(10L), 10.0, 120.0, gs, 1.0,
                sucursal(2L), null, LocalDateTime.now());

        assertEquals(120.0, r.getCostoMedio(), DELTA);
    }

    // --- aplicarCostoCompra: dedup ---

    @Test
    void aplicarCostoCompra_costoIdentico_noGuarda() {
        Moneda gs = moneda(1L);
        CostoPorProducto anterior = costoAnterior(120.0, 120.0, gs);
        stubUltimoCosto(anterior);
        stubStockReal(5.0); // no pondera → costoMedio = 120, ultimoPrecio = 120, misma moneda

        CostoPorProducto r = service.aplicarCostoCompra(producto(10L), 10.0, 120.0, gs, 1.0,
                sucursal(2L), null, LocalDateTime.now());

        assertSame(anterior, r); // devuelve el anterior, no inserta fila
        verify(repository, never()).save(any(CostoPorProducto.class));
    }

    // --- aplicarCostoCompra: guards ---

    @Test
    void aplicarCostoCompra_costoCero_bonificacion_devuelveNullSinTocarCosto() {
        CostoPorProducto r = service.aplicarCostoCompra(producto(10L), 10.0, 0.0, moneda(1L), 1.0,
                sucursal(2L), null, LocalDateTime.now());

        assertNull(r);
        verify(repository, never()).findLastByProductoId(anyLong(), any());
        verify(repository, never()).save(any(CostoPorProducto.class));
    }

    @Test
    void aplicarCostoCompra_cantidadCero_devuelveNull() {
        assertNull(service.aplicarCostoCompra(producto(10L), 0.0, 120.0, moneda(1L), 1.0,
                sucursal(2L), null, LocalDateTime.now()));
    }

    @Test
    void aplicarCostoCompra_productoSinId_devuelveNull() {
        assertNull(service.aplicarCostoCompra(new Producto(), 10.0, 120.0, moneda(1L), 1.0,
                sucursal(2L), null, LocalDateTime.now()));
    }

    // --- registrarCostoCompraManual (transferencia desde COMPRAS) ---

    @Test
    void registrarCostoCompraManual_fijaCostoAlPrecioSinPonderar() {
        Moneda gs = moneda(1L);
        stubUltimoCosto(costoAnterior(100.0, 100.0, gs)); // había costo previo 100

        CostoPorProducto r = service.registrarCostoCompraManual(producto(10L), 500.0, gs,
                sucursal(2L), null, LocalDateTime.now());

        // NO pondera: costo medio queda fijado al precio de la compra
        assertEquals(500.0, r.getCostoMedio(), DELTA);
        assertEquals(500.0, r.getUltimoPrecioCompra(), DELTA);
        assertEquals(1.0, r.getCotizacion(), DELTA);
        verify(repository, times(1)).save(any(CostoPorProducto.class));
        // nunca consulta stock por esta vía
        verify(movimientoStockService, never())
                .stockByProductoIdExcluyendoNombresSucursal(anyLong(), any());
    }

    @Test
    void registrarCostoCompraManual_mismoPrecio_noGuarda() {
        Moneda gs = moneda(1L);
        CostoPorProducto anterior = costoAnterior(500.0, 500.0, gs);
        stubUltimoCosto(anterior);

        CostoPorProducto r = service.registrarCostoCompraManual(producto(10L), 500.0, gs,
                sucursal(2L), null, LocalDateTime.now());

        assertSame(anterior, r);
        verify(repository, never()).save(any(CostoPorProducto.class));
    }

    @Test
    void registrarCostoCompraManual_precioInvalido_devuelveNull() {
        assertNull(service.registrarCostoCompraManual(producto(10L), 0.0, moneda(1L),
                sucursal(2L), null, LocalDateTime.now()));
        assertNull(service.registrarCostoCompraManual(producto(10L), null, moneda(1L),
                sucursal(2L), null, LocalDateTime.now()));
    }
}
