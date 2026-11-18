package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.GuardadoPedidoItemGraphQL;
import com.franco.dev.graphql.operaciones.compras.FacturaProveedorMobile;
import com.franco.dev.graphql.operaciones.compras.RenglonFacturaMobile;
import com.franco.dev.graphql.operaciones.compras.RepartoRenglonMobileInput;
import com.franco.dev.repository.operaciones.DocumentoRecibidoItemRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Crear la compra con solo los renglones reconocidos (PWA #124): la guarda, el reparto y el vínculo renglón → ítem. */
class CrearSoloReconocidosTest {

    @Mock private GuardadoPedidoItemGraphQL guardadoPedidoItemGraphQL;
    @Mock private DocumentoRecibidoItemRepository renglonRepository;
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

    private static DocumentoRecibidoItem renglon(long id, Long productoId, double cantidad) {
        DocumentoRecibidoItem r = new DocumentoRecibidoItem();
        r.setId(id);
        r.setOrden((int) id);
        r.setCantidad(cantidad);
        if (productoId != null) {
            Producto p = new Producto();
            p.setId(productoId);
            r.setProducto(p);
        }
        return r;
    }

    private static RenglonFacturaMobile cruce(long id, double cantidadBase) {
        RenglonFacturaMobile c = new RenglonFacturaMobile();
        c.setId(id);
        c.setCantidadBase(cantidadBase);
        c.setPrecioUnitarioBase(1000.0);
        c.setEsBonificacion(false);
        return c;
    }

    private static RepartoRenglonMobileInput reparto(long renglonId, long sucursalId, double cantidad) {
        RepartoRenglonMobileInput r = new RepartoRenglonMobileInput();
        r.setRenglonId(renglonId);
        r.setSucursalId(sucursalId);
        r.setCantidad(cantidad);
        return r;
    }

    @Test
    void soloReconocidosSinPoderLanzaElMotivo() {
        FacturaProveedorMobile f = new FacturaProveedorMobile();
        f.setPuedeConfirmar(false);
        f.setPuedeCrearSoloReconocidos(false);
        f.setMotivoNoConfirma("Faltan vincular 2 productos.");
        GraphQLException e = assertThrows(GraphQLException.class, () -> ComprasMobileService.exigirPuedeCrear(f, true));
        assertEquals("Faltan vincular 2 productos.", e.getMessage());
        // Sin el flag, igual que hoy: tampoco se puede
        assertThrows(GraphQLException.class, () -> ComprasMobileService.exigirPuedeCrear(f, false));
    }

    @Test
    void soloReconocidosConPoderNoLanza() {
        FacturaProveedorMobile f = new FacturaProveedorMobile();
        f.setPuedeConfirmar(false);
        f.setPuedeCrearSoloReconocidos(true);
        f.setMotivoNoConfirma("Falta vincular 1 producto.");
        assertDoesNotThrow(() -> ComprasMobileService.exigirPuedeCrear(f, true));
        // El flag no habilita nada si no viene pedido
        assertThrows(GraphQLException.class, () -> ComprasMobileService.exigirPuedeCrear(f, false));
        // Todos reconocidos: se crea como hoy, con o sin el flag
        FacturaProveedorMobile completa = new FacturaProveedorMobile();
        completa.setPuedeConfirmar(true);
        completa.setPuedeCrearSoloReconocidos(false);
        assertDoesNotThrow(() -> ComprasMobileService.exigirPuedeCrear(completa, true));
        assertDoesNotThrow(() -> ComprasMobileService.exigirPuedeCrear(completa, false));
    }

    @Test
    void losRenglonesDeTrabajoSonLosQueTienenProducto() {
        DocumentoRecibidoItem a = renglon(1, 50L, 2), b = renglon(2, null, 3), c = renglon(3, 60L, 1);
        List<DocumentoRecibidoItem> todos = Arrays.asList(a, b, c);
        assertEquals(Arrays.asList(a, c), ComprasMobileService.renglonesDeTrabajo(todos, true));
        assertEquals(todos, ComprasMobileService.renglonesDeTrabajo(todos, false));
    }

    @Test
    void repartoIgnoraRenglonesExcluidos() {
        DocumentoRecibidoItem a = renglon(1, 50L, 4);
        Map<Long, RenglonFacturaMobile> porId = new HashMap<>();
        porId.put(1L, cruce(1, 4));
        porId.put(3L, cruce(3, 2));
        List<RepartoRenglonMobileInput> repartos = Arrays.asList(
                reparto(1, 10, 3), reparto(1, 20, 1),
                reparto(3, 10, 2));
        Map<Long, double[]> r = ComprasMobileService.repartoPorRenglon(
                Collections.singletonList(a), porId, Arrays.asList(10L, 20L), repartos);
        assertEquals(Collections.singleton(1L), r.keySet());
        assertArrayEquals(new double[]{3, 1}, r.get(1L), 1e-9);
    }

    @Test
    void cadaRenglonQuedaVinculadoASuItem() {
        DocumentoRecibidoItem a = renglon(1, 50L, 2), c = renglon(3, 60L, 1);
        Map<Long, RenglonFacturaMobile> porId = new HashMap<>();
        porId.put(1L, cruce(1, 2));
        porId.put(3L, cruce(3, 1));
        Map<Long, double[]> partes = new HashMap<>();
        partes.put(1L, new double[]{2});
        partes.put(3L, new double[]{1});
        PedidoItem i101 = new PedidoItem();
        i101.setId(101L);
        PedidoItem i102 = new PedidoItem();
        i102.setId(102L);
        // Se crean del último al primero: el renglón 3 primero (101), después el 1 (102)
        when(guardadoPedidoItemGraphQL.guardarPedidoItemConDistribuciones(any(), any(), isNull())).thenReturn(i101, i102);
        when(renglonRepository.save(any(DocumentoRecibidoItem.class))).thenAnswer(inv -> inv.getArgument(0));
        Pedido pedido = new Pedido();
        pedido.setId(7L);

        service.crearItems(pedido, Arrays.asList(a, c), porId, Collections.singletonList(10L), partes, 1.0, 5L);

        assertSame(i101, c.getPedidoItem());
        assertSame(i102, a.getPedidoItem());
        verify(renglonRepository).save(a);
        verify(renglonRepository).save(c);
        verify(guardadoPedidoItemGraphQL, times(2)).guardarPedidoItemConDistribuciones(any(), any(), isNull());
    }
}
