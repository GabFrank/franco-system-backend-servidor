package com.franco.dev.service.productos;

import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.repository.productos.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** #611: lucro por producto en una sola consulta y con caché para las páginas siguientes. */
class LucroPorProductoServiceTest {

    private static final String INICIO = "2026-08-01T07:00:00";
    private static final String FIN = "2026-09-01T06:59:00";

    private ProductoRepository repository;
    private RelojManual reloj;
    private LucroPorProductoService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProductoRepository.class);
        reloj = new RelojManual(Instant.parse("2026-11-11T12:00:00Z"));
        service = new LucroPorProductoService(repository, reloj);
        when(repository.findLucroPorProducto(anyList(), any(), any(), anyList(), anyList(), any(), any(),
                anyBoolean(), anyBoolean(), anyBoolean()))
                .thenAnswer(inv -> new ArrayList<>(Arrays.asList(
                        dto(1L, 600.0, 10.0, 1000.0, 50.0, 0.0),
                        dto(2L, 100.0, 4.0, 3000.0, 0.0, 20.0))));
    }

    @Test
    void todasLasSucursalesVanEnUnaSolaConsulta() {
        service.calcular(INICIO, FIN, Arrays.asList(3L, 5L, 7L), null, null, null, null);

        verify(repository, times(1)).findLucroPorProducto(eq(Arrays.asList(3L, 5L, 7L)), any(), any(),
                eq(Collections.singletonList(-1L)), eq(Collections.singletonList(-1L)), isNull(), isNull(),
                eq(true), eq(false), eq(false));
    }

    @Test
    void todasLlegaComoListaConNullYNoFiltraPorSucursal() {
        service.calcular(INICIO, FIN, Collections.singletonList(null), null, null, null, null);
        service.calcular(INICIO, FIN, null, null, null, null, null);

        verify(repository, times(2)).findLucroPorProducto(eq(Collections.singletonList(-1L)), any(), any(),
                anyList(), anyList(), any(), any(), eq(false), anyBoolean(), anyBoolean());
    }

    @Test
    void calculaLosDerivadosYOrdenaPorTotalDeVenta() {
        List<LucroPorProductosDto> r = service.calcular(INICIO, FIN, null, null, null, null, null);

        assertEquals(Arrays.asList(2L, 1L), Arrays.asList(r.get(0).getProductoId(), r.get(1).getProductoId()),
                "mayor total de venta primero");

        LucroPorProductosDto p1 = r.get(1);
        assertEquals(60.0, p1.getCostoUnitario(), 1e-9);
        assertEquals(100.0, p1.getVentaMedia(), 1e-9, "venta media = total de venta / cantidad");
        assertEquals(350.0, p1.getLucro(), 1e-9, "1000 - 600 - 50 de descuento");
        assertEquals(350.0 / 600.0 * 100, p1.getMargen(), 1e-9, "margen sobre el costo");
        assertEquals(35.0, p1.getPercent(), 1e-9, "porcentaje sobre la venta");

        assertEquals(2920.0, r.get(0).getLucro(), 1e-9, "3000 - 100 + 20 de aumento");
    }

    @Test
    void sinCantidadTodoQuedaEnCero() {
        LucroPorProductosDto d = dto(9L, 0.0, 0.0, 0.0, null, null);
        LucroPorProductoService.calcularDerivados(d);
        assertEquals(0.0, d.getLucro());
        assertEquals(0.0, d.getVentaMedia());
        assertEquals(0.0, d.getMargen());
    }

    @Test
    void lasPaginasSiguientesReusanElResultado() {
        List<LucroPorProductosDto> primera = service.calcularConCache(INICIO, FIN, Arrays.asList(5L, 3L), null, null,
                null, null, true);
        List<LucroPorProductosDto> segunda = service.calcularConCache(INICIO, FIN, Arrays.asList(3L, 5L), null, null,
                null, null, false);

        assertSame(primera, segunda, "mismo filtro (el orden de las sucursales no importa)");
        verifyConsultas(1);
    }

    @Test
    void laPrimeraPaginaSiempreRecalcula() {
        service.calcularConCache(INICIO, FIN, null, null, null, null, null, true);
        service.calcularConCache(INICIO, FIN, null, null, null, null, null, true);
        verifyConsultas(2);
    }

    @Test
    void otroFiltroNoUsaLoGuardado() {
        service.calcularConCache(INICIO, FIN, Collections.singletonList(3L), null, null, null, null, true);
        service.calcularConCache(INICIO, FIN, Collections.singletonList(4L), null, null, null, null, false);
        service.calcularConCache(INICIO, FIN, Collections.singletonList(3L), null, null, 8L, null, false);
        verifyConsultas(3);
    }

    @Test
    void vencidoRecalcula() {
        service.calcularConCache(INICIO, FIN, null, null, null, null, null, true);
        reloj.avanzar(LucroPorProductoService.VIGENCIA.minusSeconds(1));
        service.calcularConCache(INICIO, FIN, null, null, null, null, null, false);
        verifyConsultas(1);

        reloj.avanzar(Duration.ofSeconds(1));
        service.calcularConCache(INICIO, FIN, null, null, null, null, null, false);
        verifyConsultas(2);
    }

    @Test
    void noGuardaMasDelMaximo() {
        for (long i = 0; i < LucroPorProductoService.MAXIMO_GUARDADOS + 5; i++) {
            service.calcularConCache(INICIO, FIN, Collections.singletonList(i), null, null, null, null, true);
        }
        assertEquals(LucroPorProductoService.MAXIMO_GUARDADOS, service.cantidadGuardados());

        // El más viejo se descartó, el más nuevo sigue.
        service.calcularConCache(INICIO, FIN, Collections.singletonList(0L), null, null, null, null, false);
        verifyConsultas(LucroPorProductoService.MAXIMO_GUARDADOS + 6);
        service.calcularConCache(INICIO, FIN, Collections.singletonList(
                (long) LucroPorProductoService.MAXIMO_GUARDADOS + 4), null, null, null, null, false);
        verifyConsultas(LucroPorProductoService.MAXIMO_GUARDADOS + 6);
    }

    @Test
    void loGuardadoEsDeSoloLectura() {
        List<LucroPorProductosDto> r = service.calcularConCache(INICIO, FIN, null, null, null, null, null, true);
        assertThrows(UnsupportedOperationException.class, () -> r.remove(0));
    }

    @Test
    void laConsultaFiltraTodasLasSucursalesJuntasYSumaLaVentaSinPresentacion() throws Exception {
        Method m = Arrays.stream(ProductoRepository.class.getMethods())
                .filter(x -> x.getName().equals("findLucroPorProducto"))
                .findFirst().orElseThrow(AssertionError::new);
        String sql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ");
        assertTrue(sql.contains("(:filtrarSucursal = false or v.sucursalId IN (:sucursalIdList))"));
        assertTrue(sql.contains("SUM(vi.precio * vi.cantidad) as totalVenta"),
                "vi.precio ya es el precio de la presentación");
    }

    private void verifyConsultas(int n) {
        verify(repository, times(n)).findLucroPorProducto(anyList(), any(), any(), anyList(), anyList(), any(),
                any(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    private static LucroPorProductosDto dto(Long id, Double costo, Double cantidad, Double venta, Double descuento,
            Double aumento) {
        return new LucroPorProductosDto(id, "P" + id, costo, cantidad, venta, 0.0, 0.0, 0.0, 0.0, descuento,
                aumento);
    }

    private static final class RelojManual extends Clock {
        private Instant ahora;

        RelojManual(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
