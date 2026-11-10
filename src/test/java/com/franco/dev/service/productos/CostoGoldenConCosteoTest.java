package com.franco.dev.service.productos;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.ConfiguracionMonedaModulo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.financiero.CambioRepository;
import com.franco.dev.repository.financiero.ConfiguracionMonedaModuloRepository;
import com.franco.dev.repository.productos.CostosPorProductoRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.financiero.CotizacionService;
import com.franco.dev.service.financiero.MonedaModuloLector;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * La tabla de oro de {@link CostoGoldenTest}, sin copiarla ni editarla, con el costeo enchufado: con
 * COSTOS sin configurar y con COSTOS = USD, las columnas de siempre tienen que dar exactamente lo
 * mismo. Las de costeo no cambian el costo de la empresa, nunca.
 */
class CostoGoldenConCosteoTest {

    private static final double DELTA = 1e-9;
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 27, 10, 0);

    static Stream<Arguments> tablaPorConfiguracion() {
        return Stream.of(Boolean.FALSE, Boolean.TRUE)
                .flatMap(enDolares -> CostoGoldenTest.tabla().map(c -> Arguments.of(enDolares ? "COSTOS=USD" : "sin configurar", c)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("tablaPorConfiguracion")
    void lasColumnasDeSiempreNoCambian(String configuracion, CostoGoldenTest.Caso c) {
        CostosPorProductoRepository repository = mock(CostosPorProductoRepository.class);
        MovimientoStockService movimientoStockService = mock(MovimientoStockService.class);
        ConfiguracionMonedaModuloRepository configuracionRepository = mock(ConfiguracionMonedaModuloRepository.class);
        CambioRepository cambioRepository = mock(CambioRepository.class);
        MonedaService monedaService = mock(MonedaService.class);
        Moneda gs = moneda(1L);
        Moneda usd = moneda(2L);
        when(monedaService.empresa()).thenReturn(gs);
        List<ConfiguracionMonedaModulo> filas = Collections.emptyList();
        if (configuracion.equals("COSTOS=USD")) {
            ConfiguracionMonedaModulo f = new ConfiguracionMonedaModulo();
            f.setModulo(ModuloMoneda.COSTOS);
            f.setMoneda(usd);
            f.setVigenteDesde(FECHA.minusDays(30));
            filas = Collections.singletonList(f);
            Cambio cambio = new Cambio();
            cambio.setValorEnGs(5700.0);
            when(cambioRepository.findLastByCambioId(2L)).thenReturn(cambio);
            when(cambioRepository.findUltimoHasta(any(), any())).thenReturn(cambio);
        }
        when(configuracionRepository.findByModuloOrderByVigenteDesdeDescIdDesc(any())).thenReturn(filas);
        CostosPorProductoService service = new CostosPorProductoService(repository, movimientoStockService,
                mock(ModificacionService.class));
        ReflectionTestUtils.setField(service, "costeoMoneda", new CosteoMoneda(
                new MonedaModuloLector(configuracionRepository, monedaService), new CotizacionService(cambioRepository)));
        when(repository.save(any(CostoPorProducto.class))).thenAnswer(inv -> inv.getArgument(0));

        CostoPorProducto anterior = null;
        if (c.tieneAnterior()) {
            anterior = new CostoPorProducto();
            anterior.setId(1L);
            anterior.setCostoMedio(c.antCostoMedio);
            anterior.setUltimoPrecioCompra(c.antUltimoPrecio);
            anterior.setMoneda(c.antMonedaId != null ? moneda(c.antMonedaId) : null);
        }
        when(repository.findLastByProductoId(anyLong(), any()))
                .thenReturn(anterior != null ? Collections.singletonList(anterior) : Collections.emptyList());
        when(movimientoStockService.stockByProductoIdExcluyendoNombresSucursal(anyLong(), any())).thenReturn(c.stockReal);

        Producto producto = new Producto();
        producto.setId(10L);
        Sucursal sucursal = new Sucursal();
        sucursal.setId(2L);
        Moneda moneda = c.monedaId != null ? moneda(c.monedaId) : null;
        CostoPorProducto r = "COMPRA".equals(String.valueOf((Object) c.via))
                ? service.aplicarCostoCompra(producto, c.cantidad, c.costoUnitario, moneda, c.cotizacion, sucursal, null, FECHA)
                : service.registrarCostoCompraManual(producto, c.costoUnitario, moneda, sucursal, null, FECHA);

        switch (String.valueOf((Object) c.resultado)) {
            case "NULO":
                assertNull(r);
                verify(repository, never()).save(any(CostoPorProducto.class));
                break;
            case "DEVUELVE_ANTERIOR":
                assertSame(anterior, r);
                verify(repository, never()).save(any(CostoPorProducto.class));
                break;
            default:
                ArgumentCaptor<CostoPorProducto> captor = ArgumentCaptor.forClass(CostoPorProducto.class);
                verify(repository, times(1)).save(captor.capture());
                CostoPorProducto g = captor.getValue();
                assertEquals(c.espCostoMedio, g.getCostoMedio(), DELTA, "costo_medio");
                assertEquals(c.espUltimoPrecio, g.getUltimoPrecioCompra(), DELTA, "ultimo_precio_compra");
                assertEquals(c.espCotizacion, g.getCotizacion(), DELTA, "cotizacion");
                assertEquals(c.espMonedaId, g.getMoneda() != null ? g.getMoneda().getId() : null, "moneda_id");
                assertNotNull(g.getMonedaCosteo(), "la fila nueva lleva su moneda de costeo");
                assertNotNull(g.getCostoMedioCosteo(), "y su costo de costeo");
        }
    }

    private static Moneda moneda(Long id) {
        Moneda m = new Moneda();
        m.setId(id);
        // Sin denominacion ni ISO, como la tabla de oro: el costo no puede depender del nombre.
        return m;
    }
}
