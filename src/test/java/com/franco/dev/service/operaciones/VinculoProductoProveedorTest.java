package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.repository.productos.ProductoProveedorRepository;
import com.franco.dev.service.productos.ProductoProveedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VinculoProductoProveedorTest {

    @Mock NotaRecepcionRepository repository;
    @Mock NotaRecepcionItemService itemService;
    @Mock NotaRecepcionItemDistribucionService distribucionService;
    @Mock PedidoItemService pedidoItemService;
    @Mock ProcesoEtapaService procesoEtapaService;
    @Mock ProductoProveedorService productoProveedorService;
    @Mock ProductoProveedorRepository vinculoRepo;

    NotaRecepcionService service;
    Proveedor proveedor;
    Pedido pedido;
    NotaRecepcion nota;

    @BeforeEach
    void init() {
        service = new NotaRecepcionService(repository, itemService, distribucionService, pedidoItemService,
                procesoEtapaService, productoProveedorService);
        proveedor = new Proveedor();
        proveedor.setId(7L);
        pedido = new Pedido();
        pedido.setId(3L);
        pedido.setProveedor(proveedor);
        nota = new NotaRecepcion();
        nota.setId(11L);
        nota.setPedido(pedido);
    }

    private NotaRecepcionItem item(Long productoId) {
        Producto p = new Producto();
        p.setId(productoId);
        NotaRecepcionItem i = new NotaRecepcionItem();
        i.setProducto(p);
        return i;
    }

    @Test
    void creaVinculoQueNoExiste() {
        when(itemService.findByNotaRecepcionId(11L)).thenReturn(Collections.singletonList(item(5L)));
        when(productoProveedorService.getRepository()).thenReturn(vinculoRepo);
        when(vinculoRepo.findByProductoIdAndProveedorId(5L, 7L)).thenReturn(null);

        service.vincularProductosConProveedor(nota, null);

        ArgumentCaptor<ProductoProveedor> c = ArgumentCaptor.forClass(ProductoProveedor.class);
        verify(productoProveedorService).save(c.capture());
        assertEquals(5L, c.getValue().getProducto().getId());
        assertSame(proveedor, c.getValue().getProveedor());
        assertSame(pedido, c.getValue().getPedido());
        assertTrue(c.getValue().getActivo());
    }

    @Test
    void reactivaVinculoInactivo() {
        ProductoProveedor v = new ProductoProveedor();
        v.setActivo(false);
        v.setMotivoDesvinculacion("ya no lo vende");
        when(itemService.findByNotaRecepcionId(11L)).thenReturn(Collections.singletonList(item(5L)));
        when(productoProveedorService.getRepository()).thenReturn(vinculoRepo);
        when(vinculoRepo.findByProductoIdAndProveedorId(5L, 7L)).thenReturn(v);

        service.vincularProductosConProveedor(nota, new Usuario());

        verify(productoProveedorService).save(v);
        assertTrue(v.getActivo());
        assertNull(v.getMotivoDesvinculacion());
    }

    @Test
    void noRepiteProducto() {
        when(itemService.findByNotaRecepcionId(11L)).thenReturn(Arrays.asList(item(5L), item(5L)));
        when(productoProveedorService.getRepository()).thenReturn(vinculoRepo);
        when(vinculoRepo.findByProductoIdAndProveedorId(5L, 7L)).thenReturn(null);

        service.vincularProductosConProveedor(nota, null);

        verify(productoProveedorService, times(1)).save(any(ProductoProveedor.class));
    }

    @Test
    void notaSinProveedorNoHaceNada() {
        pedido.setProveedor(null);

        service.vincularProductosConProveedor(nota, null);

        verifyNoInteractions(productoProveedorService);
        verifyNoInteractions(itemService);
    }
}
