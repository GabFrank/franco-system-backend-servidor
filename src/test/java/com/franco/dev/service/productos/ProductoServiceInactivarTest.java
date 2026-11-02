package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.repository.productos.ProductoRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.productos.search.ProductoSearchIndexSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Inactivar un producto inactiva sus presentaciones y precios, solo en la transición
 * activo → inactivo (la PWA da de alta productos inactivos y les carga precios antes de activarlos).
 */
class ProductoServiceInactivarTest {

    private ProductoRepository repository;
    private PresentacionService presentacionService;
    private PrecioPorSucursalService precioPorSucursalService;
    private ProductoService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProductoRepository.class);
        presentacionService = mock(PresentacionService.class);
        precioPorSucursalService = mock(PrecioPorSucursalService.class);
        // @RequiredArgsConstructor, en orden de declaración; lo que save no usa va en null
        service = new ProductoService(repository, null, null, null, null, presentacionService,
                precioPorSucursalService, null, null, null, null, mock(ModificacionService.class), null,
                mock(ProductoSearchIndexSyncService.class), null);
        when(repository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- helpers ---

    private void productoGuardadoCon(Boolean activo) {
        Producto anterior = new Producto();
        anterior.setId(1L);
        anterior.setActivo(activo);
        when(repository.findById(1L)).thenReturn(Optional.of(anterior));
    }

    private ProductoInput input(Long id, Boolean activo) {
        ProductoInput in = new ProductoInput();
        in.setId(id);
        in.setDescripcion("producto");
        in.setActivo(activo);
        return in;
    }

    private Presentacion presentacion(long id, Boolean activo) {
        Presentacion p = new Presentacion();
        p.setId(id);
        p.setDescripcion("pres " + id);
        p.setActivo(activo);
        return p;
    }

    private PrecioPorSucursal precio(long id, Boolean activo) {
        PrecioPorSucursal p = new PrecioPorSucursal();
        p.setId(id);
        p.setPrecio(1000.0 * id);
        p.setActivo(activo);
        return p;
    }

    // --- tests ---

    @Test
    void alPasarAInactivoInactivaPresentacionesYPreciosQueNoLoEstan() {
        productoGuardadoCon(true);
        Presentacion activa = presentacion(10, true);
        Presentacion sinValor = presentacion(11, null);
        Presentacion yaInactiva = presentacion(12, false);
        when(presentacionService.findByProductoId(1L)).thenReturn(Arrays.asList(activa, sinValor, yaInactiva));
        when(precioPorSucursalService.findByPresentacionId(10L))
                .thenReturn(Arrays.asList(precio(100, true), precio(101, false)));
        when(precioPorSucursalService.findByPresentacionId(11L)).thenReturn(Collections.singletonList(precio(102, null)));
        when(precioPorSucursalService.findByPresentacionId(12L)).thenReturn(Collections.singletonList(precio(103, true)));

        service.save(input(1L, false));

        ArgumentCaptor<Presentacion> presentaciones = ArgumentCaptor.forClass(Presentacion.class);
        verify(presentacionService, times(2)).save(presentaciones.capture());
        List<Presentacion> guardadas = presentaciones.getAllValues();
        assertEquals(Arrays.asList(10L, 11L), Arrays.asList(guardadas.get(0).getId(), guardadas.get(1).getId()));
        guardadas.forEach(p -> assertFalse(p.getActivo()));
        assertEquals("pres 10", guardadas.get(0).getDescripcion());

        ArgumentCaptor<PrecioPorSucursal> precios = ArgumentCaptor.forClass(PrecioPorSucursal.class);
        verify(precioPorSucursalService, times(3)).save(precios.capture());
        List<PrecioPorSucursal> preciosGuardados = precios.getAllValues();
        assertEquals(Arrays.asList(100L, 102L, 103L),
                Arrays.asList(preciosGuardados.get(0).getId(), preciosGuardados.get(1).getId(),
                        preciosGuardados.get(2).getId()));
        preciosGuardados.forEach(p -> assertFalse(p.getActivo()));
        assertEquals(100000.0, preciosGuardados.get(0).getPrecio());
        // Se guarda una copia: la instancia leída queda como «anterior» para el registro de modificaciones
        assertEquals(true, activa.getActivo());
    }

    @Test
    void productoQueYaEstabaInactivoNoTocaNada() {
        productoGuardadoCon(false);

        service.save(input(1L, false));

        verify(presentacionService, never()).findByProductoId(any());
        verify(presentacionService, never()).save(any());
        verify(precioPorSucursalService, never()).save(any());
    }

    @Test
    void productoNuevoInactivoNoTocaNada() {
        service.save(input(null, false));

        verify(presentacionService, never()).findByProductoId(any());
        verify(precioPorSucursalService, never()).save(any());
    }

    @Test
    void activarOGuardarActivoNoTocaNada() {
        productoGuardadoCon(false);
        service.save(input(1L, true));
        productoGuardadoCon(true);
        service.save(input(1L, true));

        verify(presentacionService, never()).findByProductoId(any());
        verify(presentacionService, never()).save(any());
        verify(precioPorSucursalService, never()).save(any());
    }

    @Test
    void productoSinValorDeActivoQuePasaAInactivoCuentaComoActivo() {
        productoGuardadoCon(null);
        when(presentacionService.findByProductoId(1L)).thenReturn(Collections.singletonList(presentacion(10, true)));
        when(precioPorSucursalService.findByPresentacionId(10L)).thenReturn(Collections.emptyList());

        service.save(input(1L, false));

        verify(presentacionService).save(any(Presentacion.class));
    }
}
