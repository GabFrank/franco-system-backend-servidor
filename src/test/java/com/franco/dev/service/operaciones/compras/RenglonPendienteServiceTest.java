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
import com.franco.dev.repository.operaciones.PedidoItemRepository;
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
    @Mock private PedidoItemRepository pedidoItemRepository;
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

    // --- Bloqueo al finalizar la planificación ---

    private DocumentoRecibido facturaQueEspera(Pedido pedido) {
        DocumentoRecibido doc = documento(pedido);
        doc.setEstablecimiento("001");
        doc.setPuntoExpedicion("002");
        doc.setNumero("0000123");
        when(documentoRepository.findByPedidoIdOrderByIdAsc(7L)).thenReturn(Arrays.asList(doc));
        return doc;
    }

    private static DocumentoRecibidoItem renglon(long id, DocumentoRecibido doc, String descripcion, Long productoId) {
        DocumentoRecibidoItem r = pendiente(id, doc);
        r.setOrden((int) id);
        r.setDescripcion(descripcion);
        if (productoId != null) {
            Producto p = new Producto();
            p.setId(productoId);
            r.setProducto(p);
        }
        return r;
    }

    @Test
    void bloqueaConRenglonPendiente() {
        Pedido pedido = pedido(7L);
        DocumentoRecibido doc = facturaQueEspera(pedido);
        PedidoItem item = item(pedido);
        when(pedidoItemRepository.findByPedidoId(7L)).thenReturn(Arrays.asList(item));
        DocumentoRecibidoItem cargado = renglon(1L, doc, "Galleta dulce", 3L);
        DocumentoRecibidoItem falta = renglon(2L, doc, "Yerba 500 g", null);
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(10L)).thenReturn(Arrays.asList(cargado, falta));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.exigirFacturaCompleta(7L));
        assertEquals("Falta cargar 1 renglón de la factura 001-002-0000123: Yerba 500 g. "
                + "Cargalo o marcá “No cargar” con el motivo.", e.getMessage());
    }

    @Test
    void bloqueaConRenglonSinItem() {
        Pedido pedido = pedido(7L);
        DocumentoRecibido doc = facturaQueEspera(pedido);
        when(pedidoItemRepository.findByPedidoId(7L)).thenReturn(Arrays.asList(item(pedido)));
        // Producto 7: reconocido, pero el ítem se borró del pedido y no hay otro del mismo producto
        DocumentoRecibidoItem sinItem = renglon(1L, doc, "Arroz 1 kg", 7L);
        PedidoItem borrado = new PedidoItem();
        borrado.setId(999L);
        sinItem.setPedidoItem(borrado);
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(10L)).thenReturn(Arrays.asList(sinItem));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.exigirFacturaCompleta(7L));
        assertEquals("Falta cargar 1 renglón de la factura 001-002-0000123: Arroz 1 kg. "
                + "Cargalo o marcá “No cargar” con el motivo.", e.getMessage());
    }

    @Test
    void noBloqueaConTodoCargadoODescartado() {
        Pedido pedido = pedido(7L);
        DocumentoRecibido doc = facturaQueEspera(pedido);
        PedidoItem item = item(pedido);
        PedidoItem otro = new PedidoItem();
        otro.setId(102L);
        otro.setPedido(pedido);
        Producto p9 = new Producto();
        p9.setId(9L);
        otro.setProducto(p9);
        when(pedidoItemRepository.findByPedidoId(7L)).thenReturn(Arrays.asList(item, otro));
        // Mismo producto que un ítem, sin vínculo explícito (o una DIFERENCIA de cantidad/precio): no bloquea
        DocumentoRecibidoItem porProducto = renglon(1L, doc, "Galleta dulce", 3L);
        porProducto.setCantidad(99.0);
        // Vinculado explícitamente a un ítem del pedido
        DocumentoRecibidoItem vinculado = renglon(2L, doc, "Galleta salada", 9L);
        vinculado.setPedidoItem(otro);
        DocumentoRecibidoItem descartado = renglon(3L, doc, "Bolsa", null);
        descartado.setDescartado(true);
        DocumentoRecibidoItem descartadoSinItem = renglon(4L, doc, "Flete", 50L);
        descartadoSinItem.setDescartado(true);
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(10L))
                .thenReturn(Arrays.asList(porProducto, vinculado, descartado, descartadoSinItem));

        assertDoesNotThrow(() -> service.exigirFacturaCompleta(7L));
    }

    @Test
    void noBloqueaPedidosSinFacturaDelCelular() {
        Pedido pedido = pedido(7L);
        when(documentoRepository.findByPedidoIdOrderByIdAsc(7L)).thenReturn(Arrays.asList());
        assertDoesNotThrow(() -> service.exigirFacturaCompleta(7L));

        // Facturas que no esperan la planificación (ya con nota o sin cargar al finalizar) tampoco bloquean
        DocumentoRecibido conNota = documento(pedido);
        conNota.setNotaRecepcion(new NotaRecepcion());
        DocumentoRecibido sinCargar = documento(pedido);
        sinCargar.setId(12L);
        sinCargar.setCargarAlFinalizar(false);
        when(documentoRepository.findByPedidoIdOrderByIdAsc(7L)).thenReturn(Arrays.asList(conNota, sinCargar));
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(anyLong()))
                .thenReturn(Arrays.asList(renglon(1L, conNota, "Yerba", null)));
        assertDoesNotThrow(() -> service.exigirFacturaCompleta(7L));
        assertDoesNotThrow(() -> service.exigirFacturaCompleta(null));
    }

    @Test
    void mensajeNombraHastaTresRenglones() {
        Pedido pedido = pedido(7L);
        DocumentoRecibido doc = facturaQueEspera(pedido);
        when(pedidoItemRepository.findByPedidoId(7L)).thenReturn(Arrays.asList());
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(10L)).thenReturn(Arrays.asList(
                renglon(1L, doc, "Yerba", null), renglon(2L, doc, "Azúcar", null), renglon(3L, doc, "Arroz", 7L),
                renglon(4L, doc, "Fideo", null), renglon(5L, doc, "Sal", null)));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.exigirFacturaCompleta(7L));
        assertEquals("Faltan cargar 5 renglones de la factura 001-002-0000123: Yerba, Azúcar, Arroz y 2 más. "
                + "Cargalos o marcá “No cargar” con el motivo.", e.getMessage());

        // Con dos o tres, se nombran todos
        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(10L)).thenReturn(Arrays.asList(
                renglon(1L, doc, "Yerba", null), renglon(2L, doc, "Azúcar", null)));
        e = assertThrows(GraphQLException.class, () -> service.exigirFacturaCompleta(7L));
        assertEquals("Faltan cargar 2 renglones de la factura 001-002-0000123: Yerba y Azúcar. "
                + "Cargalos o marcá “No cargar” con el motivo.", e.getMessage());

        when(renglonRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(10L)).thenReturn(Arrays.asList(
                renglon(1L, doc, "Yerba", null), renglon(2L, doc, "Azúcar", null), renglon(3L, doc, "Sal", null)));
        e = assertThrows(GraphQLException.class, () -> service.exigirFacturaCompleta(7L));
        assertEquals("Faltan cargar 3 renglones de la factura 001-002-0000123: Yerba, Azúcar y Sal. "
                + "Cargalos o marcá “No cargar” con el motivo.", e.getMessage());
    }
}
