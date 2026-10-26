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

import com.franco.dev.graphql.operaciones.compras.SugerenciaProductoMobile;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Las guardas de la factura en papel dentro del flujo de siempre y lo aprendido por descripción: se guarda siempre
 * (con o sin código) y vuelve como sugerencia preseleccionada, nunca asignada sola.
 */
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

    private static Presentacion presentacion(long id, long productoId) {
        Presentacion p = presentacion();
        p.setId(id);
        p.getProducto().setId(productoId);
        return p;
    }

    private static ProveedorCodigoProducto aprendido(Presentacion p, String codigo, double factor, int usos) {
        ProveedorCodigoProducto a = new ProveedorCodigoProducto();
        a.setPresentacion(p);
        a.setCodigoProveedor(codigo);
        a.setFactor(factor);
        a.setUsos(usos);
        return a;
    }

    private static DocumentoRecibidoItem renglon(String codigo, String descripcion) {
        DocumentoRecibidoItem r = new DocumentoRecibidoItem();
        r.setCodigoProveedor(codigo);
        r.setDescripcion(descripcion);
        return r;
    }

    @Test
    void loAprendidoPorDescripcionNoSeAsignaSolo() {
        when(aprendizajeRepository.findByProveedorIdAndDescripcionNormalizada(5L, "GALLETA DULCE"))
                .thenReturn(Collections.singletonList(aprendido(presentacion(), null, 1.0, 1)));
        DocumentoRecibidoItem r = renglon(null, "Galleta dulce");
        service.resolverProducto(r, proveedor(), Collections.emptySet());
        assertNull(r.getProducto());
        assertNull(r.getTipoMatch());
        assertNull(r.getNotaReconocimiento());
    }

    @Test
    void unaSolaPresentacionVaPreseleccionadaConSuFactor() {
        when(aprendizajeRepository.findByProveedorIdAndDescripcionNormalizada(5L, "SALADIX JAMON 6 100GR"))
                .thenReturn(Collections.singletonList(aprendido(presentacion(), "1007167", 30.0, 3)));
        // Renglón sin código: lo aprendido de la factura electrónica (con código) sirve igual
        List<SugerenciaProductoMobile> s = service.aprendidosPorDescripcion(renglon(null, "Saladix jamón 6*100gr"), proveedor());
        assertEquals(1, s.size());
        assertTrue(s.get(0).getPreseleccionada());
        assertEquals(30.0, s.get(0).getFactor());
        assertEquals(30L, s.get(0).getPresentacionId());
        assertEquals("APRENDIDO_DESCRIPCION", s.get(0).getTipo());
        assertTrue(s.get(0).getMotivo().contains("3 veces"));
    }

    @Test
    void conCodigoSinVinculoTambienSugierePorDescripcion() {
        when(aprendizajeRepository.findByProveedorIdAndDescripcionNormalizada(5L, "GALLETA DULCE"))
                .thenReturn(Collections.singletonList(aprendido(presentacion(), null, 1.0, 1)));
        // La IA leyó mal el código: no hay vínculo para «ABX», pero la descripción sí está
        DocumentoRecibidoItem r = renglon("ABX", "Galleta dulce");
        service.resolverProducto(r, proveedor(), Collections.emptySet());
        assertNull(r.getProducto());
        verify(aprendizajeRepository).findByProveedorIdAndCodigoProveedor(5L, "ABX");
        assertTrue(service.aprendidosPorDescripcion(r, proveedor()).get(0).getPreseleccionada());
    }

    @Test
    void laMismaPresentacionDesdeDosVinculosSeJunta() {
        Presentacion p = presentacion();
        when(aprendizajeRepository.findByProveedorIdAndDescripcionNormalizada(5L, "GALLETA DULCE"))
                .thenReturn(Arrays.asList(aprendido(p, "ABC", 1.0, 2), aprendido(p, null, 1.0, 1)));
        List<SugerenciaProductoMobile> s = service.aprendidosPorDescripcion(renglon(null, "GALLETA DULCE"), proveedor());
        assertEquals(1, s.size());
        assertTrue(s.get(0).getPreseleccionada());
        assertTrue(s.get(0).getMotivo().contains("3 veces"));
    }

    @Test
    void variosProductosConLaMismaDescripcionNoPreseleccionaNinguno() {
        when(aprendizajeRepository.findByProveedorIdAndDescripcionNormalizada(5L, "GALLETA DULCE"))
                .thenReturn(Arrays.asList(aprendido(presentacion(30, 3), "A1", 1.0, 1),
                        aprendido(presentacion(31, 4), "A2", 1.0, 5)));
        List<SugerenciaProductoMobile> s = service.aprendidosPorDescripcion(renglon(null, "GALLETA DULCE"), proveedor());
        assertEquals(2, s.size());
        assertFalse(s.get(0).getPreseleccionada());
        assertFalse(s.get(1).getPreseleccionada());
        // La más usada primero
        assertEquals(31L, s.get(0).getPresentacionId());
    }

    @Test
    void mismaPresentacionConOtroFactorEsAmbiguo() {
        Presentacion p = presentacion();
        when(aprendizajeRepository.findByProveedorIdAndDescripcionNormalizada(5L, "GALLETA DULCE"))
                .thenReturn(Arrays.asList(aprendido(p, "A1", 1.0, 1), aprendido(p, "A2", 12.0, 1)));
        List<SugerenciaProductoMobile> s = service.aprendidosPorDescripcion(renglon(null, "GALLETA DULCE"), proveedor());
        assertEquals(2, s.size());
        assertTrue(s.stream().noneMatch(SugerenciaProductoMobile::getPreseleccionada));
    }

    @Test
    void soloInactivosNoSugiereYAvisa() {
        Presentacion p = presentacion();
        p.getProducto().setActivo(false);
        when(aprendizajeRepository.findByProveedorIdAndDescripcionNormalizada(5L, "GALLETA DULCE"))
                .thenReturn(Collections.singletonList(aprendido(p, null, 1.0, 1)));
        DocumentoRecibidoItem r = renglon(null, "Galleta dulce");
        assertTrue(service.aprendidosPorDescripcion(r, proveedor()).isEmpty());
        service.resolverProducto(r, proveedor(), Collections.emptySet());
        assertNull(r.getProducto());
        assertTrue(r.getNotaReconocimiento().startsWith("Esta descripción estaba vinculada a «GALLETA»"));
    }

    @Test
    void sinProveedorNoBuscaPorDescripcion() {
        assertTrue(service.aprendidosPorDescripcion(renglon(null, "GALLETA DULCE"), null).isEmpty());
        verify(aprendizajeRepository, never()).findByProveedorIdAndDescripcionNormalizada(any(), any());
    }

    @Test
    void conCodigoAprendeTambienLaDescripcion() {
        when(aprendizajeRepository.findByProveedorIdAndCodigoProveedor(5L, "1007167")).thenReturn(Optional.empty());
        service.aprender(proveedor(), renglon(" 1007167 ", "Saladix jamón 6*100gr"), presentacion(), 30.0);
        ArgumentCaptor<ProveedorCodigoProducto> guardado = ArgumentCaptor.forClass(ProveedorCodigoProducto.class);
        verify(aprendizajeRepository).save(guardado.capture());
        assertEquals("1007167", guardado.getValue().getCodigoProveedor());
        assertEquals("SALADIX JAMON 6 100GR", guardado.getValue().getDescripcionNormalizada());
        assertEquals(30.0, guardado.getValue().getFactor());
    }

    @Test
    void sinCodigoAprendePorDescripcion() {
        when(aprendizajeRepository.findByProveedorIdAndCodigoProveedorIsNullAndDescripcionNormalizada(5L, "GALLETA DULCE"))
                .thenReturn(Optional.empty());
        service.aprender(proveedor(), renglon(null, "Galleta dulce"), presentacion(), 1.0);
        ArgumentCaptor<ProveedorCodigoProducto> guardado = ArgumentCaptor.forClass(ProveedorCodigoProducto.class);
        verify(aprendizajeRepository).save(guardado.capture());
        assertNull(guardado.getValue().getCodigoProveedor());
        assertEquals("GALLETA DULCE", guardado.getValue().getDescripcionNormalizada());
    }
}
