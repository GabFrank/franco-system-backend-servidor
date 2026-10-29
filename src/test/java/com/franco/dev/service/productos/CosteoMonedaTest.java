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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * La doble escritura del costo: las columnas de siempre en guaranies (las fija CostoGoldenTest) y
 * las de costeo en la moneda del modulo COSTOS. Se arma el servicio con el lector y la cotizacion
 * reales sobre repositorios simulados, para probar la cadena entera.
 */
class CosteoMonedaTest {

    private static final double DELTA = 1e-9;
    private static final LocalDateTime VIGENCIA = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 10, 9, 0);

    private CostosPorProductoRepository repository;
    private MovimientoStockService movimientoStockService;
    private ConfiguracionMonedaModuloRepository configuracionRepository;
    private CambioRepository cambioRepository;
    private CostosPorProductoService service;

    private final Moneda gs = moneda(1L, "GUARANI", "PYG", 0);
    private final Moneda usd = moneda(2L, "DOLAR", "USD", 2);
    private final Moneda brl = moneda(3L, "REAL", "BRL", 2);

    @BeforeEach
    void setUp() {
        repository = mock(CostosPorProductoRepository.class);
        movimientoStockService = mock(MovimientoStockService.class);
        configuracionRepository = mock(ConfiguracionMonedaModuloRepository.class);
        cambioRepository = mock(CambioRepository.class);
        MonedaService monedaService = mock(MonedaService.class);
        when(monedaService.empresa()).thenReturn(gs);
        when(configuracionRepository.findByModuloOrderByVigenteDesdeDescIdDesc(any())).thenReturn(Collections.emptyList());
        CosteoMoneda costeo = new CosteoMoneda(new MonedaModuloLector(configuracionRepository, monedaService),
                new CotizacionService(cambioRepository));
        service = new CostosPorProductoService(repository, movimientoStockService, mock(ModificacionService.class));
        ReflectionTestUtils.setField(service, "costeoMoneda", costeo);
        when(repository.save(any(CostoPorProducto.class))).thenAnswer(inv -> inv.getArgument(0));
        sinAnterior();
    }

    // --- sin configurar: copia exacta ---

    @Test
    void sinConfigurar_lasColumnasDeCosteoSonCopia() {
        anterior(4000.0, 4000.0, null, null);
        stock(30.0);
        CostoPorProducto g = compra(10.0, 7000.0, gs, 1.0);
        assertEquals(5000.0, g.getCostoMedio(), DELTA);
        assertSame(gs, g.getMonedaCosteo());
        assertEquals(0, BigDecimal.ONE.compareTo(g.getCotizacionCosteo()));
        assertEquals(0, BigDecimal.valueOf(g.getCostoMedio()).compareTo(g.getCostoMedioCosteo()));
        assertEquals(0, BigDecimal.valueOf(g.getUltimoPrecioCompra()).compareTo(g.getUltimoPrecioCompraCosteo()));
        verifyNoInteractions(cambioRepository);
    }

    @Test
    void sinConfigurar_compraEnDolares_copiaLosGuaranies() {
        stock(5.0);
        CostoPorProducto g = compra(5.0, 2.5, usd, 5700.0);
        assertEquals(14250.0, g.getCostoMedio(), DELTA);
        assertEquals(0, new BigDecimal("14250").compareTo(g.getCostoMedioCosteo()));
    }

    @Test
    void configuracionDesactivada_vuelveALaCopia() {
        ConfiguracionMonedaModulo fila = costos(usd);
        fila.setActivo(false);
        when(configuracionRepository.findByModuloOrderByVigenteDesdeDescIdDesc(ModuloMoneda.COSTOS))
                .thenReturn(Collections.singletonList(fila));
        stock(10.0);
        CostoPorProducto g = compra(10.0, 5000.0, gs, 1.0);
        assertSame(gs, g.getMonedaCosteo());
        assertEquals(0, new BigDecimal("5000").compareTo(g.getCostoMedioCosteo()));
    }

    // --- COSTOS = USD ---

    @Test
    void enDolares_compraEnDolares_usaElPrecioDeLaNota() {
        costosEn(usd);
        stock(10.0);
        CostoPorProducto g = compra(10.0, 10.0, usd, 5700.0);
        assertEquals(57000.0, g.getCostoMedio(), DELTA); // la columna de siempre, en Gs
        assertSame(usd, g.getMonedaCosteo());
        assertEquals(new BigDecimal("10.0000"), g.getUltimoPrecioCompraCosteo());
        assertEquals(new BigDecimal("10.0000"), g.getCostoMedioCosteo());
        assertEquals(0, new BigDecimal("5700").compareTo(g.getCotizacionCosteo()));
        verifyNoInteractions(cambioRepository); // la cotizacion es la de la nota
    }

    @Test
    void enDolares_pondaraEnDolaresYNoEnGuaranies() {
        costosEn(usd);
        anterior(45600.0, 45600.0, usd, new BigDecimal("8.0000"));
        stock(20.0);
        // La cotizacion subio de 5.700 a 6.000: en Gs el costo anterior se revaluaria; en USD no.
        CostoPorProducto g = compra(10.0, 10.0, usd, 6000.0);
        assertEquals(new BigDecimal("9.0000"), g.getCostoMedioCosteo());
        assertEquals((10 * 45600.0 + 10 * 60000.0) / 20, g.getCostoMedio(), DELTA);
    }

    @Test
    void enDolares_compraEnGuaranies_seConvierteConLaCotizacionDeCompras() {
        costosEn(usd);
        cambio(usd, 5700.0);
        stock(10.0);
        CostoPorProducto g = compra(10.0, 57000.0, gs, 1.0);
        assertEquals(57000.0, g.getUltimoPrecioCompra(), DELTA);
        assertEquals(new BigDecimal("10.0000"), g.getUltimoPrecioCompraCosteo());
        assertEquals(0, new BigDecimal("5700").compareTo(g.getCotizacionCosteo()));
    }

    @Test
    void enDolares_compraEnReales_pasaPorElGuarani() {
        costosEn(usd);
        cambio(usd, 5700.0);
        stock(10.0);
        // 50 BRL a 1.140 = 57.000 Gs = 10 USD
        CostoPorProducto g = compra(10.0, 50.0, brl, 1140.0);
        assertEquals(57000.0, g.getUltimoPrecioCompra(), DELTA);
        assertEquals(new BigDecimal("10.0000"), g.getUltimoPrecioCompraCosteo());
    }

    @Test
    void enDolares_redondeaUnaSolaVezACuatroDecimalesAunqueElDolarTengaDos() {
        costosEn(usd);
        cambio(usd, 5700.0);
        stock(10.0);
        // 10.000 / 5.700 = 1,7543859...
        CostoPorProducto g = compra(10.0, 10000.0, gs, 1.0);
        assertEquals(new BigDecimal("1.7544"), g.getUltimoPrecioCompraCosteo());
        assertEquals(new BigDecimal("1.7544"), g.getCostoMedioCosteo());
    }

    // --- punto de partida ---

    @Test
    void puntoDePartida_laFilaViejaAportaSuCostoEnGsALaCotizacionDeVigencia() {
        costosEn(usd);
        // Fila de antes de configurar: sin costeo. 45.600 Gs a 5.700 (cotizacion de vigente_desde) = 8 USD.
        anterior(45600.0, 45600.0, gs, null);
        when(cambioRepository.findUltimoHasta(2L, VIGENCIA)).thenReturn(cambioDe(5700.0));
        stock(20.0);
        CostoPorProducto g = compra(10.0, 10.0, usd, 6000.0);
        assertEquals(new BigDecimal("9.0000"), g.getCostoMedioCosteo());
    }

    @Test
    void puntoDePartida_usaLaCotizacionGuardadaAlConfigurar_aunqueElMercadoHayaCambiado() {
        // El scheduler pisa el mercado de la ultima fila de cambio: lo de hoy (6.100) no cuenta.
        ConfiguracionMonedaModulo fila = costos(usd);
        fila.setCotizacionPartida(new BigDecimal("5700"));
        when(configuracionRepository.findByModuloOrderByVigenteDesdeDescIdDesc(ModuloMoneda.COSTOS))
                .thenReturn(Collections.singletonList(fila));
        cambio(usd, 6100.0);
        anterior(45600.0, 45600.0, gs, null);
        stock(20.0);
        CostoPorProducto g = compra(10.0, 10.0, usd, 6000.0);
        // Partida 45.600 / 5.700 = 8 USD; (10*8 + 10*10) / 20 = 9
        assertEquals(new BigDecimal("9.0000"), g.getCostoMedioCosteo());
    }

    @Test
    void puntoDePartida_sinCotizacionDeVigencia_noPonderaPeroGuarda() {
        costosEn(usd);
        anterior(45600.0, 45600.0, gs, null);
        stock(20.0);
        CostoPorProducto g = compra(10.0, 10.0, usd, 6000.0);
        assertEquals(new BigDecimal("10.0000"), g.getCostoMedioCosteo());
    }

    // --- sin cotizacion: el costo de siempre se guarda igual ---

    @Test
    void sinCotizacionDeLaMonedaDeCosteo_quedaEnNullYElCostoSeGuarda() {
        costosEn(usd);
        stock(10.0);
        CostoPorProducto g = compra(10.0, 5000.0, gs, 1.0);
        assertEquals(5000.0, g.getCostoMedio(), DELTA);
        assertNull(g.getMonedaCosteo());
        assertNull(g.getCostoMedioCosteo());
        assertNull(g.getUltimoPrecioCompraCosteo());
    }

    // --- via manual ---

    @Test
    void viaManual_convierteSinPonderar() {
        costosEn(usd);
        cambio(usd, 5700.0);
        anterior(10000.0, 10000.0, gs, new BigDecimal("3.0000"));
        when(repository.findLastByProductoId(anyLong(), any())).thenReturn(Collections.singletonList(anteriorFila));
        CostoPorProducto r = service.registrarCostoCompraManual(producto(), 57000.0, gs, sucursal(), null, FECHA);
        assertEquals(57000.0, r.getCostoMedio(), DELTA);
        assertEquals(new BigDecimal("10.0000"), r.getCostoMedioCosteo());
        assertEquals(new BigDecimal("10.0000"), r.getUltimoPrecioCompraCosteo());
    }

    // --- lo que lee el cliente ---

    @Test
    void puntoDePartidaPublico_redondea_yUsaLoGuardadoSiEsLaMismaMoneda() {
        CosteoMoneda costeo = (CosteoMoneda) ReflectionTestUtils.getField(service, "costeoMoneda");
        costosEn(usd);
        when(cambioRepository.findUltimoHasta(2L, VIGENCIA)).thenReturn(cambioDe(5700.0));
        CostoPorProducto vieja = fila(10000.0, 10000.0, gs, null);
        assertEquals(new BigDecimal("1.7544"), costeo.puntoDePartida(vieja, usd).get());
        CostoPorProducto nueva = fila(45600.0, 45600.0, usd, new BigDecimal("8.0000"));
        nueva.setMonedaCosteo(usd);
        assertEquals(new BigDecimal("8.0000"), costeo.puntoDePartida(nueva, usd).get());
        assertEquals(new BigDecimal("10000"), costeo.puntoDePartida(vieja, gs).get().setScale(0));
    }

    @Test
    void ultimoPrecioDePartida_filaViejaEnMonedaDeLaNota() {
        // Antes del 09/07 ultimo_precio_compra quedaba en la moneda de la nota: 0,39 USD a 6.210 con
        // costo medio 2.400 Gs (caso real, producto 7 de la copia de bodega).
        CosteoMoneda costeo = (CosteoMoneda) ReflectionTestUtils.getField(service, "costeoMoneda");
        costosEn(usd);
        cambio(usd, 5890.0);
        CostoPorProducto vieja = fila(2400.0, 0.39, usd, null);
        vieja.setCotizacion(6210.0);
        assertEquals(new BigDecimal("0.3900"), costeo.ultimoPrecioDePartida(vieja, usd).get());
        // En otra moneda de costeo pasa por Gs con la cotizacion de la nota: 0,39 * 6.210 = 2.421,9 Gs.
        assertEquals(0, new BigDecimal("2421.9").compareTo(costeo.ultimoPrecioDePartida(vieja, gs).get()));
        // Ya en Gs (posterior al 09/07): 57.000 Gs con cotizacion 5.700 no es 57.000 USD.
        CostoPorProducto nueva = fila(57000.0, 57000.0, usd, null);
        nueva.setCotizacion(5700.0);
        assertEquals(new BigDecimal("9.6774"), costeo.ultimoPrecioDePartida(nueva, usd).get());
    }

    // --- helpers ---

    private CostoPorProducto anteriorFila;

    private CostoPorProducto compra(double cantidad, double costo, Moneda moneda, Double cotizacion) {
        service.aplicarCostoCompra(producto(), cantidad, costo, moneda, cotizacion, sucursal(), null, FECHA);
        ArgumentCaptor<CostoPorProducto> captor = ArgumentCaptor.forClass(CostoPorProducto.class);
        verify(repository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    private void costosEn(Moneda m) {
        when(configuracionRepository.findByModuloOrderByVigenteDesdeDescIdDesc(ModuloMoneda.COSTOS))
                .thenReturn(Collections.singletonList(costos(m)));
    }

    private static ConfiguracionMonedaModulo costos(Moneda m) {
        ConfiguracionMonedaModulo f = new ConfiguracionMonedaModulo();
        f.setId(1L);
        f.setModulo(ModuloMoneda.COSTOS);
        f.setMoneda(m);
        f.setVigenteDesde(VIGENCIA);
        f.setActivo(true);
        return f;
    }

    private void cambio(Moneda m, double valor) {
        when(cambioRepository.findLastByCambioId(m.getId())).thenReturn(cambioDe(valor));
        when(cambioRepository.findUltimoHasta(eq(m.getId()), any())).thenReturn(cambioDe(valor));
    }

    private static Cambio cambioDe(double valor) {
        Cambio c = new Cambio();
        c.setValorEnGs(valor);
        return c;
    }

    private void sinAnterior() {
        when(repository.findLastByProductoId(anyLong(), any())).thenReturn(Collections.emptyList());
    }

    private void anterior(Double costoMedio, Double ultimo, Moneda monedaCosteo, BigDecimal costoMedioCosteo) {
        anteriorFila = fila(costoMedio, ultimo, gs, costoMedioCosteo);
        anteriorFila.setMonedaCosteo(monedaCosteo);
        List<CostoPorProducto> l = Collections.singletonList(anteriorFila);
        when(repository.findLastByProductoId(anyLong(), any())).thenReturn(l);
    }

    private static CostoPorProducto fila(Double costoMedio, Double ultimo, Moneda moneda, BigDecimal costoMedioCosteo) {
        CostoPorProducto c = new CostoPorProducto();
        c.setId(1L);
        c.setCostoMedio(costoMedio);
        c.setUltimoPrecioCompra(ultimo);
        c.setMoneda(moneda);
        c.setCostoMedioCosteo(costoMedioCosteo);
        c.setUltimoPrecioCompraCosteo(costoMedioCosteo);
        return c;
    }

    private void stock(double s) {
        when(movimientoStockService.stockByProductoIdExcluyendoNombresSucursal(anyLong(), any())).thenReturn(s);
    }

    private static Producto producto() {
        Producto p = new Producto();
        p.setId(10L);
        return p;
    }

    private static Sucursal sucursal() {
        Sucursal s = new Sucursal();
        s.setId(2L);
        return s;
    }

    private static Moneda moneda(Long id, String denominacion, String iso, int decimales) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setCodigoIso(iso);
        m.setDecimales(decimales);
        return m;
    }
}
