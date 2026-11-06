package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.DocumentoRecibidoItemRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Un renglón que quedó afuera de la compra (sin producto) se carga desde el desktop como ítem del pedido, y vuelve a
 * quedar pendiente si se borra ese ítem.
 */
class RenglonPendienteServiceTest {

    @Mock private DocumentoRecibidoItemRepository renglonRepository;
    @Mock private AprendizajeProveedor aprendizaje;
    @InjectMocks private RenglonPendienteService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(renglonRepository.save(any(DocumentoRecibidoItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static Pedido pedido(long id) {
        Pedido p = new Pedido();
        p.setId(id);
        return p;
    }

    private static DocumentoRecibido documento(Pedido pedido) {
        DocumentoRecibido d = new DocumentoRecibido();
        d.setId(10L);
        d.setPedido(pedido);
        d.setCargarAlFinalizar(true);
        Proveedor proveedor = new Proveedor();
        proveedor.setId(5L);
        d.setProveedor(proveedor);
        return d;
    }

    private static DocumentoRecibidoItem pendiente(long id, DocumentoRecibido doc) {
        DocumentoRecibidoItem r = new DocumentoRecibidoItem();
        r.setId(id);
        r.setDocumentoRecibido(doc);
        r.setCodigoProveedor("ABC-1");
        r.setDescripcion("Galleta dulce");
        return r;
    }

    private static PedidoItem item(Pedido pedido) {
        Producto producto = new Producto();
        producto.setId(3L);
        Presentacion presentacion = new Presentacion();
        presentacion.setId(30L);
        presentacion.setProducto(producto);
        presentacion.setCantidad(12.0);
        PedidoItem i = new PedidoItem();
        i.setId(101L);
        i.setPedido(pedido);
        i.setProducto(producto);
        i.setPresentacionCreacion(presentacion);
        return i;
    }

    @Test
    void vinculaUnRenglonPendiente() {
        Pedido pedido = pedido(7L);
        DocumentoRecibido doc = documento(pedido);
        DocumentoRecibidoItem r = pendiente(1L, doc);
        PedidoItem item = item(pedido);
        when(renglonRepository.findById(1L)).thenReturn(Optional.of(r));

        service.vincularAlItem(1L, item);

        assertSame(item.getProducto(), r.getProducto());
        assertSame(item.getPresentacionCreacion(), r.getPresentacion());
        assertEquals(12.0, r.getFactor());
        assertEquals("MANUAL", r.getTipoMatch());
        assertSame(item, r.getPedidoItem());
        verify(renglonRepository).save(r);
        verify(aprendizaje).aprender(doc.getProveedor(), r, item.getPresentacionCreacion(), 12.0);
    }

    @Test
    void rechazaRenglonYaCargado() {
        Pedido pedido = pedido(7L);
        DocumentoRecibidoItem r = pendiente(1L, documento(pedido));
        r.setProducto(new Producto());
        when(renglonRepository.findById(1L)).thenReturn(Optional.of(r));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.vincularAlItem(1L, item(pedido)));
        assertEquals("Ese renglón de la factura ya está cargado.", e.getMessage());
        verify(renglonRepository, never()).save(any());
        verifyNoInteractions(aprendizaje);
    }

    @Test
    void rechazaRenglonDeOtroPedido() {
        DocumentoRecibidoItem r = pendiente(1L, documento(pedido(8L)));
        when(renglonRepository.findById(1L)).thenReturn(Optional.of(r));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.vincularAlItem(1L, item(pedido(7L))));
        assertEquals("Ese renglón no es de una factura de este pedido.", e.getMessage());
        assertNull(r.getProducto());
        verify(renglonRepository, never()).save(any());
    }

    @Test
    void rechazaRenglonDescartado() {
        Pedido pedido = pedido(7L);
        DocumentoRecibidoItem r = pendiente(1L, documento(pedido));
        r.setDescartado(true);
        when(renglonRepository.findById(1L)).thenReturn(Optional.of(r));

        assertThrows(GraphQLException.class, () -> service.vincularAlItem(1L, item(pedido)));
        assertNull(r.getProducto());
        verify(renglonRepository, never()).save(any());
        verifyNoInteractions(aprendizaje);
    }

    @Test
    void borrarElItemDevuelveElRenglonAPendiente() {
        Pedido pedido = pedido(7L);
        DocumentoRecibidoItem r = pendiente(1L, documento(pedido));
        PedidoItem item = item(pedido);
        r.setProducto(item.getProducto());
        r.setPresentacion(item.getPresentacionCreacion());
        r.setFactor(12.0);
        r.setTipoMatch("MANUAL");
        r.setPedidoItem(item);
        r.setObservacion("Precio distinto");
        when(renglonRepository.findByPedidoItemId(101L)).thenReturn(Arrays.asList(r));

        service.liberarDelItem(101L);

        assertNull(r.getProducto());
        assertNull(r.getPresentacion());
        assertNull(r.getFactor());
        assertNull(r.getTipoMatch());
        assertNull(r.getPedidoItem());
        assertNull(r.getObservacion());
        assertFalse(r.getDescartado());
        verify(renglonRepository).save(r);
    }

    @Test
    void liberarNoTocaRenglonesDeFacturaConNota() {
        Pedido pedido = pedido(7L);
        DocumentoRecibido conNota = documento(pedido);
        conNota.setNotaRecepcion(new NotaRecepcion());
        DocumentoRecibidoItem r = pendiente(1L, conNota);
        PedidoItem item = item(pedido);
        r.setProducto(item.getProducto());
        r.setTipoMatch("MANUAL");
        r.setPedidoItem(item);
        when(renglonRepository.findByPedidoItemId(101L)).thenReturn(Arrays.asList(r));

        service.liberarDelItem(101L);

        assertSame(item.getProducto(), r.getProducto());
        assertSame(item, r.getPedidoItem());
        verify(renglonRepository, never()).save(any());
    }
}
