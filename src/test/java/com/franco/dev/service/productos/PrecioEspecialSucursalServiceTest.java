package com.franco.dev.service.productos;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.PrecioEspecialSucursal;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.graphql.productos.input.PrecioEspecialSucursalInput;
import com.franco.dev.repository.productos.PrecioEspecialSucursalRepository;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrecioEspecialSucursalServiceTest {

    private PrecioEspecialSucursalRepository repository;
    private PrecioPorSucursalService precioService;
    private SucursalService sucursalService;
    private PrecioEspecialSucursalService service;
    private PrecioPorSucursal heineken;
    private Usuario autor;

    @BeforeEach
    void setUp() {
        repository = mock(PrecioEspecialSucursalRepository.class);
        precioService = mock(PrecioPorSucursalService.class);
        sucursalService = mock(SucursalService.class);
        service = new PrecioEspecialSucursalService(repository, precioService, sucursalService);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        heineken = new PrecioPorSucursal();
        heineken.setId(10L);
        heineken.setPrecio(6000.0);
        when(precioService.findById(10L)).thenReturn(Optional.of(heineken));
        for (long id : new long[]{1L, 3L}) {
            Sucursal s = new Sucursal();
            s.setId(id);
            s.setNombre("SUC " + id);
            when(sucursalService.findById(id)).thenReturn(Optional.of(s));
        }
        when(repository.findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(any(), any())).thenReturn(Collections.emptyList());
        autor = new Usuario();
        autor.setId(3L);
    }

    private static PrecioEspecialSucursalInput input(Double precio, String desde, String hasta, Long... sucursales) {
        PrecioEspecialSucursalInput in = new PrecioEspecialSucursalInput();
        in.setPrecioId(10L);
        in.setPrecio(precio);
        in.setFechaDesde(desde);
        in.setFechaHasta(hasta);
        in.setSucursalIds(Arrays.asList(sucursales));
        return in;
    }

    private static PrecioEspecialSucursal existente(Long id, Long sucursalId, LocalDate desde, LocalDate hasta) {
        PrecioEspecialSucursal e = new PrecioEspecialSucursal();
        e.setId(id);
        Sucursal s = new Sucursal();
        s.setId(sucursalId);
        s.setNombre("SUC " + sucursalId);
        e.setSucursal(s);
        e.setFechaDesde(desde);
        e.setFechaHasta(hasta);
        e.setActivo(true);
        e.setPrecio(5500.0);
        return e;
    }

    @Test
    void creaUnaFilaPorSucursalConAutorYFechas() {
        List<PrecioEspecialSucursal> r = service.crear(input(5000.0, "2026-10-01", "2026-10-31", 1L, 3L), autor);
        assertEquals(2, r.size());
        assertEquals(1L, r.get(0).getSucursal().getId());
        assertEquals(3L, r.get(1).getSucursal().getId());
        assertSame(heineken, r.get(0).getPrecioPorSucursal());
        assertEquals(5000.0, r.get(0).getPrecio());
        assertEquals(LocalDate.of(2026, 10, 1), r.get(0).getFechaDesde());
        assertEquals(LocalDate.of(2026, 10, 31), r.get(0).getFechaHasta());
        assertTrue(r.get(0).getActivo());
        assertSame(autor, r.get(0).getUsuario());
        assertNotNull(r.get(0).getCreadoEn());
    }

    @Test
    void sucursalesRepetidasSeGuardanUnaVez() {
        assertEquals(1, service.crear(input(5000.0, null, null, 1L, 1L), autor).size());
    }

    @Test
    void siUnaSucursalSeSuperponeNoSeGuardaNinguna() {
        when(repository.findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(10L, 3L))
                .thenReturn(Collections.singletonList(existente(99L, 3L, LocalDate.of(2026, 10, 15), null)));
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.crear(input(5000.0, "2026-10-01", "2026-10-31", 1L, 3L), autor));
        assertTrue(e.getMessage().contains("SUC 3"));
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaPrecioNoPositivoRangoInvertidoSinSucursalesYSucursalCentral() {
        assertThrows(GraphQLException.class, () -> service.crear(input(0.0, null, null, 1L), autor));
        assertThrows(GraphQLException.class, () -> service.crear(input(5000.0, "2026-10-31", "2026-10-01", 1L), autor));
        assertThrows(GraphQLException.class, () -> service.crear(input(5000.0, null, null), autor));
        assertThrows(GraphQLException.class, () -> service.crear(input(5000.0, null, null, 0L), autor));
        verify(repository, never()).save(any());
    }

    @Test
    void superposicionConRangosAbiertos() {
        LocalDate a = LocalDate.of(2026, 10, 1), b = LocalDate.of(2026, 10, 31);
        assertTrue(PrecioEspecialSucursalService.seSuperponen(null, null, a, b));
        assertTrue(PrecioEspecialSucursalService.seSuperponen(a, b, b, null));      // comparten el ultimo dia
        assertFalse(PrecioEspecialSucursalService.seSuperponen(a, b, b.plusDays(1), null));
        assertFalse(PrecioEspecialSucursalService.seSuperponen(null, a.minusDays(1), a, b));
    }

    @Test
    void editarExcluyeLaPropiaFilaDeLaSuperposicion() {
        PrecioEspecialSucursal fila = existente(5L, 1L, null, null);
        fila.setPrecioPorSucursal(heineken);
        when(repository.findById(5L)).thenReturn(Optional.of(fila));
        when(repository.findByPrecioPorSucursalIdAndSucursalIdAndActivoTrue(10L, 1L)).thenReturn(Collections.singletonList(fila));
        PrecioEspecialSucursal r = service.editar(5L, 4500.0, "2026-10-01", null, autor);
        assertEquals(4500.0, r.getPrecio());
        assertEquals(LocalDate.of(2026, 10, 1), r.getFechaDesde());
    }

    @Test
    void noSeEditaUnoCortado() {
        PrecioEspecialSucursal fila = existente(5L, 1L, null, null);
        fila.setActivo(false);
        fila.setPrecioPorSucursal(heineken);
        when(repository.findById(5L)).thenReturn(Optional.of(fila));
        assertThrows(GraphQLException.class, () -> service.editar(5L, 4500.0, null, null, autor));
    }

    @Test
    void cortarDesactivaSinBorrarYEsIdempotente() {
        PrecioEspecialSucursal fila = existente(5L, 1L, null, null);
        when(repository.findById(5L)).thenReturn(Optional.of(fila));
        assertFalse(service.cortar(5L, autor).getActivo());
        assertFalse(service.cortar(5L, autor).getActivo());
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void lasEscriturasSonTransaccionalesDeSpring() throws Exception {
        Class<?> s = PrecioEspecialSucursalService.class;
        assertNotNull(s.getMethod("crear", PrecioEspecialSucursalInput.class, Usuario.class).getAnnotation(Transactional.class));
        assertNotNull(s.getMethod("editar", Long.class, Double.class, String.class, String.class, Usuario.class).getAnnotation(Transactional.class));
        assertNotNull(s.getMethod("cortar", Long.class, Usuario.class).getAnnotation(Transactional.class));
    }
}
