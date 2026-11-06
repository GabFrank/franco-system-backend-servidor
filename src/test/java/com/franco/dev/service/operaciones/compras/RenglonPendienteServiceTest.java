package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.ProcesoEtapa;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.compras.FacturaPendientePedido;
import com.franco.dev.graphql.operaciones.compras.RenglonPendienteFactura;
import com.franco.dev.repository.operaciones.DocumentoRecibidoRepository;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.ProcesoEtapaService;
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
import java.util.List;
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
    @Mock private DocumentoRecibidoRepository documentoRepository;
    @Mock private ProcesoEtapaService procesoEtapaService;
    @Mock private MonedaService monedaService;
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

    // ---- Descartar, restaurar y la consulta del desktop ----

    private void creacionEnProceso(long pedidoId, boolean enProceso) {
        ProcesoEtapa etapa = new ProcesoEtapa();
        etapa.setTipoEtapa(ProcesoEtapaTipo.CREACION);
        etapa.setEstadoEtapa(enProceso ? ProcesoEtapaEstado.EN_PROCESO : ProcesoEtapaEstado.COMPLETADA);
        when(procesoEtapaService.getEtapaByPedidoAndTipo(pedidoId, ProcesoEtapaTipo.CREACION)).thenReturn(Optional.of(etapa));
    }

    private static Usuario usuario() {
        Persona persona = new Persona();
        persona.setNombre("JUAN PEREZ");
        Usuario u = new Usuario();
        u.setId(9L);
        u.setNickname("JPEREZ");
        u.setPersona(persona);
        return u;
    }

    private static Moneda moneda(long id, String denominacion, String simbolo) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setSimbolo(simbolo);
        return m;
    }

    @Test
    void descartarExigeMotivo() {
        Pedido pedido = pedido(7L);
        DocumentoRecibidoItem r = pendiente(1L, documento(pedido));
        when(renglonRepository.findById(1L)).thenReturn(Optional.of(r));
        creacionEnProceso(7L, true);

        for (String motivo : new String[]{null, "", "   "}) {
            GraphQLException e = assertThrows(GraphQLException.class, () -> service.descartar(1L, motivo, usuario()));
            assertEquals("Escribí por qué no se carga este renglón.", e.getMessage());
        }
        assertFalse(r.getDescartado());
        verify(renglonRepository, never()).save(any());
    }

    @Test
    void descartarSoloPendientes() {
        Pedido pedido = pedido(7L);
        DocumentoRecibido doc = documento(pedido);
        creacionEnProceso(7L, true);
        DocumentoRecibidoItem cargado = pendiente(1L, doc);
        cargado.setProducto(new Producto());
        DocumentoRecibidoItem yaDescartado = pendiente(2L, doc);
        yaDescartado.setDescartado(true);
        when(renglonRepository.findById(1L)).thenReturn(Optional.of(cargado));
        when(renglonRepository.findById(2L)).thenReturn(Optional.of(yaDescartado));

        assertThrows(GraphQLException.class, () -> service.descartar(1L, "No vino", usuario()));
        assertThrows(GraphQLException.class, () -> service.descartar(2L, "No vino", usuario()));
        assertFalse(cargado.getDescartado());
        verify(renglonRepository, never()).save(any());

        // Un pendiente sí, pero solo con la planificación en proceso
        DocumentoRecibidoItem r = pendiente(3L, doc);
        when(renglonRepository.findById(3L)).thenReturn(Optional.of(r));
        creacionEnProceso(7L, false);
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.descartar(3L, "No vino", usuario()));
        assertEquals("Solo se puede descartar con la planificación en proceso.", e.getMessage());
        assertFalse(r.getDescartado());

        creacionEnProceso(7L, true);
        Usuario u = usuario();
        service.descartar(3L, "  No vino en el camión  ", u);
        assertTrue(r.getDescartado());
        assertEquals("No vino en el camión", r.getMotivoDescarte());
        assertSame(u, r.getDescartadoPor());
        assertNotNull(r.getDescartadoEn());
        verify(renglonRepository).save(r);
    }

    @Test
    void restaurarVuelveAPendiente() {
        Pedido pedido = pedido(7L);
        DocumentoRecibidoItem r = pendiente(1L, documento(pedido));
        r.setDescartado(true);
        r.setMotivoDescarte("No vino");
        r.setDescartadoPor(usuario());
        r.setDescartadoEn(java.time.LocalDateTime.now());
        when(renglonRepository.findById(1L)).thenReturn(Optional.of(r));

        creacionEnProceso(7L, false);
        assertThrows(GraphQLException.class, () -> service.restaurar(1L));
        assertTrue(r.getDescartado());

        creacionEnProceso(7L, true);
        service.restaurar(1L);
        assertFalse(r.getDescartado());
        assertNull(r.getMotivoDescarte());
        assertNull(r.getDescartadoPor());
        assertNull(r.getDescartadoEn());
        assertNull(r.getProducto());
        verify(renglonRepository).save(r);

        // Uno que no está descartado no se restaura
        DocumentoRecibidoItem otro = pendiente(2L, documento(pedido));
        when(renglonRepository.findById(2L)).thenReturn(Optional.of(otro));
        assertThrows(GraphQLException.class, () -> service.restaurar(2L));
    }

    private DocumentoRecibidoItem renglonDe70Mil(DocumentoRecibido doc) {
        DocumentoRecibidoItem r = pendiente(1L, doc);
        r.setOrden(1);
        r.setCantidad(10.0);
        r.setPrecioUnitario(7500.0);
        r.setDescuentoUnitario(500.0);
        r.setTotal(70000.0);
        return r;
    }

    @Test
    void precioPedidoConvertidoAOtraMoneda() {
        Pedido pedido = pedido(7L);
        pedido.setMoneda(moneda(2L, "DOLAR", "US$"));
        pedido.setCotizacion(7000.0);
        DocumentoRecibido doc = documento(pedido);
        doc.setMoneda("PYG");
        DocumentoRecibidoItem r = renglonDe70Mil(doc);
        when(documentoRepository.findByPedidoIdOrderByIdAsc(7L)).thenReturn(Arrays.asList(doc));
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(10L)).thenReturn(Arrays.asList(r));
        when(monedaService.findByDescripcion("GUARANI")).thenReturn(moneda(1L, "GUARANI", "Gs."));

        List<FacturaPendientePedido> facturas = service.pendientesDelPedido(7L);

        assertEquals(1, facturas.size());
        assertEquals("Gs.", facturas.get(0).getMonedaSimbolo());
        assertEquals(1.0, facturas.get(0).getRenglones().get(0).getPrecioPedido(), 1e-9);
        assertEquals(70000.0, facturas.get(0).getMontoPendiente(), 1e-9);

        // Sin cotización guardada en el pedido no se inventa una: el desktop deja el precio vacío
        pedido.setCotizacion(null);
        assertNull(service.pendientesDelPedido(7L).get(0).getRenglones().get(0).getPrecioPedido());
    }

    @Test
    void precioPedidoMismaMoneda() {
        Pedido pedido = pedido(7L);
        pedido.setMoneda(moneda(1L, "GUARANI", "Gs."));
        pedido.setCotizacion(1.0);
        DocumentoRecibido doc = documento(pedido);
        doc.setMoneda("PYG");
        DocumentoRecibidoItem r = renglonDe70Mil(doc);
        when(documentoRepository.findByPedidoIdOrderByIdAsc(7L)).thenReturn(Arrays.asList(doc));
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(10L)).thenReturn(Arrays.asList(r));

        RenglonPendienteFactura rp = service.pendientesDelPedido(7L).get(0).getRenglones().get(0);
        assertEquals(7000.0, rp.getPrecioPedido(), 1e-9);

        // Sin total: precio unitario menos el descuento
        r.setTotal(null);
        rp = service.pendientesDelPedido(7L).get(0).getRenglones().get(0);
        assertEquals(7000.0, rp.getPrecioPedido(), 1e-9);
    }

    @Test
    void factorFacturaAPedido() {
        assertEquals(1.0, CruceFactura.factorFacturaAPedido(true, null, null));
        assertEquals(7000.0, CruceFactura.factorFacturaAPedido(false, 7000.0, 1.0), 1e-9);
        assertEquals(1.0 / 7000, CruceFactura.factorFacturaAPedido(false, 1.0, 7000.0), 1e-12);
        assertNull(CruceFactura.factorFacturaAPedido(false, 1.0, null));
        assertNull(CruceFactura.factorFacturaAPedido(false, null, 7000.0));
    }

    @Test
    void consultaSoloTraeFacturasSinNotaYConCargarAlFinalizar() {
        Pedido pedido = pedido(7L);
        pedido.setMoneda(moneda(1L, "GUARANI", "Gs."));
        DocumentoRecibido buena = documento(pedido);
        buena.setMoneda("PYG");
        DocumentoRecibido conNota = documento(pedido);
        conNota.setId(11L);
        conNota.setNotaRecepcion(new NotaRecepcion());
        DocumentoRecibido sinCargar = documento(pedido);
        sinCargar.setId(12L);
        sinCargar.setCargarAlFinalizar(false);
        DocumentoRecibido todoCargado = documento(pedido);
        todoCargado.setId(13L);
        when(documentoRepository.findByPedidoIdOrderByIdAsc(7L))
                .thenReturn(Arrays.asList(buena, conNota, sinCargar, todoCargado));

        DocumentoRecibidoItem cargado = pendiente(1L, buena);
        cargado.setOrden(1);
        cargado.setProducto(new Producto());
        DocumentoRecibidoItem falta = pendiente(2L, buena);
        falta.setOrden(2);
        falta.setCantidad(2.0);
        falta.setTotal(100.0);
        DocumentoRecibidoItem descartado = pendiente(3L, buena);
        descartado.setOrden(3);
        descartado.setCantidad(1.0);
        descartado.setTotal(40.0);
        descartado.setDescartado(true);
        descartado.setMotivoDescarte("No vino");
        descartado.setDescartadoPor(usuario());
        descartado.setVencimiento(java.time.LocalDate.of(2027, 3, 1));
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(10L)).thenReturn(Arrays.asList(cargado, falta, descartado));
        DocumentoRecibidoItem otroCargado = pendiente(4L, todoCargado);
        otroCargado.setProducto(new Producto());
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(13L)).thenReturn(Arrays.asList(otroCargado));
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(11L)).thenReturn(Arrays.asList(pendiente(5L, conNota)));
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(12L)).thenReturn(Arrays.asList(pendiente(6L, sinCargar)));

        List<FacturaPendientePedido> facturas = service.pendientesDelPedido(7L);

        assertEquals(1, facturas.size());
        FacturaPendientePedido f = facturas.get(0);
        assertEquals(10L, f.getDocumentoId());
        assertEquals(100.0, f.getMontoPendiente(), 1e-9);
        assertEquals(40.0, f.getMontoDescartado(), 1e-9);
        assertEquals(2, f.getRenglones().size());
        assertEquals(2L, f.getRenglones().get(0).getId());
        assertFalse(f.getRenglones().get(0).getDescartado());
        RenglonPendienteFactura d = f.getRenglones().get(1);
        assertEquals(3L, d.getId());
        assertTrue(d.getDescartado());
        assertEquals("No vino", d.getMotivoDescarte());
        assertEquals("JUAN PEREZ", d.getDescartadoPor());
        assertEquals("2027-03-01", d.getVencimiento());
    }
}
