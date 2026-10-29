package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Sorteo;
import com.franco.dev.domain.operaciones.SorteoSucursal;
import com.franco.dev.domain.operaciones.enums.ModoParticipacionSorteo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.SorteoInput;
import com.franco.dev.graphql.operaciones.input.SorteoPremioInput;
import com.franco.dev.repository.operaciones.SorteoRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.productos.ProductoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SorteoServiceTest {

    private SorteoRepository repository;
    private SucursalService sucursalService;
    private ProductoService productoService;
    private JdbcTemplate jdbc;
    private SorteoService service;
    private Usuario autor;

    @BeforeEach
    void setUp() {
        repository = mock(SorteoRepository.class);
        sucursalService = mock(SucursalService.class);
        productoService = mock(ProductoService.class);
        jdbc = mock(JdbcTemplate.class);
        // 2026-10-28 02:00 UTC = 2026-10-27 23:00 en -03
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"), SorteoService.ZONA);
        service = new SorteoService(repository, sucursalService, productoService, jdbc, clock);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.existsByNombreIgnoreCaseAndActivoTrueAndIdNot(anyString(), anyLong())).thenReturn(false);
        for (long id : new long[]{1L, 3L}) {
            Sucursal s = new Sucursal();
            s.setId(id);
            when(sucursalService.findById(id)).thenReturn(Optional.of(s));
        }
        Producto p = new Producto();
        p.setId(7L);
        when(productoService.findById(7L)).thenReturn(Optional.of(p));
        autor = new Usuario();
        autor.setId(3L);
    }

    private static SorteoPremioInput premio(String desc, Long productoId, Integer cant) {
        SorteoPremioInput p = new SorteoPremioInput();
        p.setDescripcion(desc);
        p.setProductoId(productoId);
        p.setCantidad(cant);
        return p;
    }

    private static SorteoInput input() {
        SorteoInput in = new SorteoInput();
        in.setNombre("Sorteo Navidad");
        in.setMontoMinimo(100000.0);
        in.setModoParticipacion("UNA_POR_VENTA");
        in.setFechaDesde("2026-11-01");
        in.setFechaHasta("2026-12-24");
        in.setFechaSorteo("2026-12-26");
        in.setTodasSucursales(false);
        in.setSucursalIds(Arrays.asList(1L, 3L));
        in.setPremios(Arrays.asList(premio("Canasta", null, 1), premio(null, 7L, 2)));
        return in;
    }

    private static Sorteo existente() {
        Sorteo s = new Sorteo();
        s.setId(5L);
        s.setNombre("Viejo");
        s.setMontoMinimo(new BigDecimal("100000.0"));
        s.setModoParticipacion(ModoParticipacionSorteo.UNA_POR_VENTA);
        s.setIncluyeTarjeta(true);
        s.setTodasSucursales(false);
        s.setActivo(true);
        for (long id : new long[]{1L, 3L}) {
            SorteoSucursal ss = new SorteoSucursal();
            ss.setSorteo(s);
            ss.setSucursalId(id);
            s.getSucursales().add(ss);
        }
        return s;
    }

    private void conParticipaciones(boolean si) {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(5L))).thenReturn(si);
    }

    private static void falla(String mensaje, Runnable r) {
        GraphQLException e = assertThrows(GraphQLException.class, r::run);
        assertEquals(mensaje, e.getMessage());
    }

    @Test
    void creaConPremiosYSucursales() {
        Sorteo s = service.guardar(input(), autor);
        assertEquals("Sorteo Navidad", s.getNombre());
        assertEquals(0, new BigDecimal("100000").compareTo(s.getMontoMinimo()));
        assertEquals(2, s.getPremios().size());
        assertEquals(1, s.getPremios().get(0).getOrden());
        assertEquals(2, s.getPremios().get(1).getOrden());
        assertEquals(7L, s.getPremios().get(1).getProducto().getId());
        assertSame(s, s.getPremios().get(0).getSorteo());
        assertEquals(2, s.getSucursales().size());
        assertEquals(LocalDate.of(2026, 11, 1), s.getFechaDesde());
        assertTrue(s.getActivo());
        assertSame(autor, s.getUsuario());
        assertNotNull(s.getCreadoEn());
    }

    @Test
    void desdeNullSeGuardaComoHoyMenosTres() {
        SorteoInput in = input();
        in.setFechaDesde(null);
        assertEquals(LocalDate.of(2026, 10, 27), service.guardar(in, autor).getFechaDesde());
    }

    @Test
    void rechazaMinimoCero() {
        SorteoInput in = input();
        in.setMontoMinimo(0.0);
        falla("El monto mínimo tiene que ser mayor a cero.", () -> service.guardar(in, autor));
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaSucursalCentral() {
        SorteoInput in = input();
        in.setSucursalIds(Arrays.asList(1L, 0L));
        falla("El central (sucursal 0) no vende: no lleva sorteos.", () -> service.guardar(in, autor));
    }

    @Test
    void rechazaSucursalInexistenteYVacia() {
        SorteoInput in = input();
        in.setSucursalIds(Collections.singletonList(99L));
        falla("La sucursal 99 no existe.", () -> service.guardar(in, autor));
        in.setSucursalIds(Collections.emptyList());
        falla("Elegí al menos una sucursal.", () -> service.guardar(in, autor));
    }

    @Test
    void todasSucursalesNoGuardaFilas() {
        SorteoInput in = input();
        in.setTodasSucursales(true);
        in.setSucursalIds(null);
        assertTrue(service.guardar(in, autor).getSucursales().isEmpty());
    }

    @Test
    void rechazaSinPremios() {
        SorteoInput in = input();
        in.setPremios(Collections.emptyList());
        falla("Cargá al menos un premio.", () -> service.guardar(in, autor));
        in.setPremios(Collections.singletonList(premio("  ", null, 1)));
        falla("Cada premio necesita una descripción o un producto.", () -> service.guardar(in, autor));
        in.setPremios(Collections.singletonList(premio("X", null, 0)));
        falla("La cantidad de cada premio tiene que ser al menos 1.", () -> service.guardar(in, autor));
        in.setPremios(Collections.singletonList(premio(null, 99L, 1)));
        falla("El producto 99 no existe.", () -> service.guardar(in, autor));
    }

    @Test
    void rechazaSinFechaDeSorteo() {
        SorteoInput in = input();
        in.setFechaSorteo(null);
        falla("Falta la fecha del sorteo.", () -> service.guardar(in, autor));
        in.setFechaSorteo("  ");
        falla("Falta la fecha del sorteo.", () -> service.guardar(in, autor));
    }

    @Test
    void rechazaRangoInvertido() {
        SorteoInput in = input();
        in.setFechaDesde("2026-12-25");
        in.setFechaHasta("2026-12-24");
        falla("La fecha hasta no puede ser anterior a la fecha desde.", () -> service.guardar(in, autor));
    }

    @Test
    void rechazaNombreDuplicadoActivo() {
        when(repository.existsByNombreIgnoreCaseAndActivoTrueAndIdNot("Sorteo Navidad", -1L)).thenReturn(true);
        falla("Ya hay un sorteo activo con el nombre \"Sorteo Navidad\".", () -> service.guardar(input(), autor));
    }

    @Test
    void cantidadFijaRequerida() {
        SorteoInput in = input();
        in.setModoParticipacion("CANTIDAD_FIJA");
        falla("La cantidad fija de tickets tiene que ser al menos 1.", () -> service.guardar(in, autor));
        in.setCantidadFija(3);
        assertEquals(3, service.guardar(in, autor).getCantidadFija());
    }

    @Test
    void rechazaModoInvalidoYTopeCero() {
        SorteoInput in = input();
        in.setModoParticipacion("XX");
        falla("El modo de participación no es válido.", () -> service.guardar(in, autor));
        in.setModoParticipacion("POR_MULTIPLO");
        in.setTopePorVenta(0);
        falla("El tope de tickets por venta tiene que ser al menos 1.", () -> service.guardar(in, autor));
    }

    @Test
    void editarConParticipacionesCambiandoMinimoFalla() {
        when(repository.findById(5L)).thenReturn(Optional.of(existente()));
        conParticipaciones(true);
        SorteoInput in = input();
        in.setId(5L);
        in.setMontoMinimo(200000.0);
        falla("El sorteo ya tiene participaciones: no se pueden cambiar el monto, el modo ni las sucursales. Cortalo y creá uno nuevo.",
                () -> service.guardar(in, autor));
        verify(repository, never()).save(any());
    }

    @Test
    void editarConParticipacionesCambiandoNombreYPremiosAnda() {
        Sorteo viejo = existente();
        when(repository.findById(5L)).thenReturn(Optional.of(viejo));
        conParticipaciones(true);
        SorteoInput in = input();
        in.setId(5L);
        in.setNombre("Nuevo nombre");
        in.setSucursalIds(Arrays.asList(3L, 1L)); // mismo conjunto, otro orden
        in.setPremios(Collections.singletonList(premio("Tele", null, 1)));
        Sorteo s = service.guardar(in, autor);
        assertEquals("Nuevo nombre", s.getNombre());
        assertEquals(1, s.getPremios().size());
        assertEquals(2, s.getSucursales().size());
        verify(repository).existsByNombreIgnoreCaseAndActivoTrueAndIdNot("Nuevo nombre", 5L);
    }

    @Test
    void editarSinParticipacionesPermiteCambiarSucursales() {
        when(repository.findById(5L)).thenReturn(Optional.of(existente()));
        conParticipaciones(false);
        SorteoInput in = input();
        in.setId(5L);
        in.setSucursalIds(Collections.singletonList(3L));
        in.setMontoMinimo(50000.0);
        Sorteo s = service.guardar(in, autor);
        assertEquals(1, s.getSucursales().size());
        assertEquals(3L, s.getSucursales().get(0).getSucursalId());
    }

    @Test
    void editarSucursalesAplicaSoloLaDiferencia() {
        Sorteo viejo = existente();
        SorteoSucursal uno = viejo.getSucursales().get(0);
        when(repository.findById(5L)).thenReturn(Optional.of(viejo));
        conParticipaciones(false);
        Sucursal s5 = new Sucursal();
        s5.setId(5L);
        when(sucursalService.findById(5L)).thenReturn(Optional.of(s5));
        SorteoInput in = input();
        in.setId(5L);
        in.setSucursalIds(Arrays.asList(1L, 5L));
        Sorteo s = service.guardar(in, autor);
        assertEquals(2, s.getSucursales().size());
        assertSame(uno, s.getSucursales().get(0));
        assertEquals(1L, uno.getSucursalId());
        assertEquals(5L, s.getSucursales().get(1).getSucursalId());
        assertNull(s.getSucursales().get(1).getId());
    }

    @Test
    void rechazaMontoNaNInfinitoYRangoContraDesdeGuardado() {
        SorteoInput in = input();
        in.setMontoMinimo(Double.NaN);
        falla("El monto mínimo tiene que ser mayor a cero.", () -> service.guardar(in, autor));
        in.setMontoMinimo(Double.POSITIVE_INFINITY);
        falla("El monto mínimo tiene que ser mayor a cero.", () -> service.guardar(in, autor));
        Sorteo viejo = existente();
        viejo.setFechaDesde(LocalDate.of(2026, 11, 10));
        when(repository.findById(5L)).thenReturn(Optional.of(viejo));
        conParticipaciones(false);
        SorteoInput ed = input();
        ed.setId(5L);
        ed.setFechaDesde(null);
        ed.setFechaHasta("2026-11-01");
        falla("La fecha hasta no puede ser anterior a la fecha desde.", () -> service.guardar(ed, autor));
    }

    @Test
    void editarCortadoFalla() {
        Sorteo viejo = existente();
        viejo.setActivo(false);
        when(repository.findById(5L)).thenReturn(Optional.of(viejo));
        SorteoInput in = input();
        in.setId(5L);
        falla("El sorteo está cortado: creá uno nuevo.", () -> service.guardar(in, autor));
    }

    @Test
    void cortarEsIdempotente() {
        Sorteo s = existente();
        when(repository.findById(5L)).thenReturn(Optional.of(s));
        assertFalse(service.cortar(5L, autor).getActivo());
        assertFalse(service.cortar(5L, autor).getActivo());
        verify(repository, times(1)).save(s);
    }

    @Test
    void filtrarNormalizaTextoYUsaHoyMenosTres() {
        service.filtrar("  navidad ", true, 0, 20);
        verify(repository).filtrar(eq("NAVIDAD"), eq(true), eq(LocalDate.of(2026, 10, 27)), any());
        service.filtrar("   ", null, 0, 20);
        verify(repository).filtrar(eq(null), eq(false), eq(LocalDate.of(2026, 10, 27)), any());
    }
}
