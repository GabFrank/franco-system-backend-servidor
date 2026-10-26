package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.ProveedorCodigoProducto;
import com.franco.dev.repository.operaciones.DocumentoRecibidoRepository;
import com.franco.dev.repository.productos.ProveedorCodigoProductoRepository;
import com.franco.dev.service.operaciones.PedidoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Las guardas de la factura en papel dentro del flujo de siempre y lo aprendido por descripción. */
class ComprasMobileFotoTest {

    @Mock private ComprasSecurityService seguridad;
    @Mock private DocumentoRecibidoRepository documentoRepository;
    @Mock private PedidoService pedidoService;
    @Mock private ProveedorCodigoProductoRepository aprendizajeRepository;
    @InjectMocks private ComprasMobileService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private DocumentoRecibido sinConfirmar() {
        DocumentoRecibido d = new DocumentoRecibido();
        d.setId(10L);
        d.setOrigen(DocumentoRecibido.ORIGEN_FOTO);
        d.setCabeceraConfirmada(false);
        return d;
    }

    @Test
    void crearCompraConCabeceraSinConfirmarSeRechaza() {
        when(documentoRepository.findById(10L)).thenReturn(Optional.of(sinConfirmar()));
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.crearCompraDesdeFactura(
                10L, null, null, 1L, null, null, null));
        assertEquals(ComprasMobileService.CABECERA_SIN_CONFIRMAR, e.getMessage());
    }

    @Test
    void confirmarFacturaConCabeceraSinConfirmarSeRechaza() {
        when(documentoRepository.findById(10L)).thenReturn(Optional.of(sinConfirmar()));
        Pedido pedido = new Pedido();
        pedido.setId(7L);
        when(pedidoService.findById(7L)).thenReturn(Optional.of(pedido));
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.confirmarFactura(10L, 7L));
        assertEquals(ComprasMobileService.CABECERA_SIN_CONFIRMAR, e.getMessage());
    }

    private static Presentacion presentacion() {
        Producto producto = new Producto();
        producto.setId(3L);
        producto.setDescripcion("GALLETA");
        Presentacion p = new Presentacion();
        p.setId(30L);
        p.setProducto(producto);
        p.setCantidad(1.0);
        return p;
    }

    private static Proveedor proveedor() {
        Proveedor p = new Proveedor();
        p.setId(5L);
        return p;
    }

    @Test
    void sinCodigoUsaLoAprendidoPorDescripcion() {
        ProveedorCodigoProducto aprendido = new ProveedorCodigoProducto();
        aprendido.setPresentacion(presentacion());
        aprendido.setFactor(1.0);
        when(aprendizajeRepository.findByProveedorIdAndCodigoProveedorIsNullAndDescripcionNormalizada(eq(5L), anyString()))
                .thenReturn(Optional.of(aprendido));
        DocumentoRecibidoItem r = new DocumentoRecibidoItem();
        r.setDescripcion("Galleta dulce");
        service.resolverProducto(r, proveedor(), Collections.emptySet());
        assertEquals("APRENDIDO_DESCRIPCION", r.getTipoMatch());
        assertEquals(30L, r.getPresentacion().getId());
    }

    @Test
    void conCodigoNoBuscaPorDescripcion() {
        ProveedorCodigoProducto aprendido = new ProveedorCodigoProducto();
        aprendido.setPresentacion(presentacion());
        aprendido.setFactor(1.0);
        when(aprendizajeRepository.findByProveedorIdAndCodigoProveedorIsNullAndDescripcionNormalizada(eq(5L), anyString()))
                .thenReturn(Optional.of(aprendido));
        DocumentoRecibidoItem r = new DocumentoRecibidoItem();
        r.setCodigoProveedor("ABC");
        r.setDescripcion("Galleta dulce");
        service.resolverProducto(r, proveedor(), Collections.emptySet());
        assertNull(r.getProducto());
        verify(aprendizajeRepository).findByProveedorIdAndCodigoProveedor(5L, "ABC");
        verify(aprendizajeRepository, never()).findByProveedorIdAndCodigoProveedorIsNullAndDescripcionNormalizada(any(), any());
    }
}
