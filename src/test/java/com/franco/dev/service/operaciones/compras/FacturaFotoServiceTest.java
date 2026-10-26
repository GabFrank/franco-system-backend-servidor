package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.operaciones.DocumentoRecibidoFoto;
import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.graphql.operaciones.compras.AvisoFacturaMobile;
import com.franco.dev.graphql.operaciones.compras.CabeceraFacturaFotoInput;
import com.franco.dev.graphql.operaciones.compras.CabeceraFacturaFotoMobile;
import com.franco.dev.graphql.operaciones.compras.FacturaProveedorMobile;
import com.franco.dev.repository.operaciones.DocumentoRecibidoFotoRepository;
import com.franco.dev.repository.operaciones.DocumentoRecibidoItemRepository;
import com.franco.dev.repository.operaciones.DocumentoRecibidoRepository;
import com.franco.dev.service.ia.FacturaIaLeida;
import com.franco.dev.service.ia.OpenAiVisionService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.operaciones.ProcesoEtapaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.utilitarios.CalcularVerificadorRuc;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** La factura en papel: leer las fotos, crear el documento sin confirmar y confirmar la cabecera. */
class FacturaFotoServiceTest {

    @Mock private ComprasSecurityService seguridad;
    @Mock private ComprasMobileService compras;
    @Mock private OpenAiVisionService lector;
    @Mock private DocumentoRecibidoRepository documentos;
    @Mock private DocumentoRecibidoItemRepository renglones;
    @Mock private DocumentoRecibidoFotoRepository fotos;
    @Mock private PedidoService pedidoService;
    @Mock private ProveedorService proveedorService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ProcesoEtapaService procesoEtapaService;
    @InjectMocks private FacturaFotoService service;
    /** El de verdad (espiado), con el repositorio simulado: la regla de repetidos se prueba entera. */
    private DocumentoRecibidoService documentoRecibidoService;
    private AutoCloseable mocks;

    private static final String RUC = "80012345";
    private static final String RUC_OK = RUC + "-" + CalcularVerificadorRuc.getDigitoVerificador(RUC, 11);
    private static final String RUC_MAL = RUC + "-" + ((CalcularVerificadorRuc.getDigitoVerificador(RUC, 11) + 1) % 10);

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        DocumentoRecibidoService real = new DocumentoRecibidoService();
        ReflectionTestUtils.setField(real, "repository", documentos);
        ReflectionTestUtils.setField(real, "itemRepository", renglones);
        ReflectionTestUtils.setField(real, "procesoEtapaService", procesoEtapaService);
        documentoRecibidoService = spy(real);
        ReflectionTestUtils.setField(service, "documentoRecibidoService", documentoRecibidoService);
        when(documentos.save(any(DocumentoRecibido.class))).thenAnswer(inv -> {
            DocumentoRecibido d = inv.getArgument(0);
            if (d.getId() == null) d.setId(10L);
            return d;
        });
        when(renglones.save(any(DocumentoRecibidoItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fotos.save(any(DocumentoRecibidoFoto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // =================================================================================== leer

    @Test
    void leerSinFacturaNoCreaDocumento() throws IOException {
        FacturaIaLeida noEs = new FacturaIaLeida();
        noEs.setError("NO_ES_DOCUMENTO_VALIDO");
        when(lector.leer(anyList())).thenReturn(noEs);
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.leer(Collections.singletonList(jpeg()), null));
        assertEquals(FacturaFotoService.NO_SE_PUDO_LEER, e.getMessage());

        FacturaIaLeida sinItems = factura(RUC_OK);
        sinItems.getItems().clear();
        when(lector.leer(anyList())).thenReturn(sinItems);
        e = assertThrows(GraphQLException.class, () -> service.leer(Collections.singletonList(jpeg()), null));
        assertEquals(FacturaFotoService.NO_SE_PUDO_LEER, e.getMessage());

        verify(documentos, never()).save(any());
        verify(fotos, never()).save(any());
    }

    @Test
    void rucConCerosSeGuardaComoSifen() throws IOException {
        when(lector.leer(anyList())).thenReturn(factura("00" + RUC_OK));
        CabeceraFacturaFotoMobile c = service.leer(Collections.singletonList(jpeg()), null);
        ArgumentCaptor<DocumentoRecibido> doc = ArgumentCaptor.forClass(DocumentoRecibido.class);
        verify(documentos).save(doc.capture());
        assertEquals(RUC, doc.getValue().getRucEmisor());
        assertTrue(c.getRucValido());
        verify(documentos).buscarMismaFactura(RUC, 1, 12345678, "001", "002", "0001234");
    }

    @Test
    void leerUnaFacturaYaCargadaNoCreaNada() throws IOException {
        when(lector.leer(anyList())).thenReturn(factura(RUC_OK));
        DocumentoRecibido otro = new DocumentoRecibido();
        otro.setId(3L);
        NotaRecepcion nota = new NotaRecepcion();
        nota.setNumero(1234);
        nota.setPedido(pedido(99L, null));
        otro.setNotaRecepcion(nota);
        when(documentos.buscarMismaFactura(RUC, 1, 12345678, "001", "002", "0001234"))
                .thenReturn(Collections.singletonList(otro));
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.leer(Collections.singletonList(jpeg()), null));
        assertEquals("Esta factura ya está cargada: nota 1234 del pedido 99.", e.getMessage());
        verify(documentos, never()).save(any());
    }

    @Test
    void leerCreaDocumentoFotoSinConfirmar() throws IOException {
        when(lector.leer(anyList())).thenReturn(factura(RUC_OK));
        Proveedor proveedor = proveedor(5L, RUC_OK);
        when(compras.proveedorPorRuc(RUC)).thenReturn(proveedor);

        CabeceraFacturaFotoMobile c = service.leer(Arrays.asList(jpeg(), png()), null);

        ArgumentCaptor<DocumentoRecibido> doc = ArgumentCaptor.forClass(DocumentoRecibido.class);
        verify(documentos, atLeastOnce()).save(doc.capture());
        DocumentoRecibido d = doc.getValue();
        assertEquals(DocumentoRecibido.ORIGEN_FOTO, d.getOrigen());
        assertFalse(d.getCabeceraConfirmada());
        assertEquals(1, d.getTipoDocumento());
        assertNull(d.getCdc());
        assertEquals(RUC, d.getRucEmisor());
        assertEquals("001", d.getEstablecimiento());
        assertEquals("002", d.getPuntoExpedicion());
        assertEquals("0001234", d.getNumero());
        assertEquals(12345678, d.getTimbrado());
        assertEquals("gpt-4o", d.getIaModelo());
        assertEquals("timbrado", d.getIaDudosos());
        assertSame(proveedor, d.getProveedor());

        ArgumentCaptor<DocumentoRecibidoItem> items = ArgumentCaptor.forClass(DocumentoRecibidoItem.class);
        verify(renglones, times(3)).save(items.capture());
        List<DocumentoRecibidoItem> r = items.getAllValues();
        assertEquals(Arrays.asList(1, 2, 3), Arrays.asList(r.get(0).getOrden(), r.get(1).getOrden(), r.get(2).getOrden()));
        assertEquals(1, r.get(0).getAfectacionIva());
        assertEquals(3, r.get(2).getAfectacionIva());
        assertEquals(11000.0, r.get(0).getPrecioUnitario());
        // Sin total del ítem: cantidad × precio
        assertEquals(2 * 5000.0, r.get(1).getTotal());
        assertEquals("cantidad", r.get(1).getIaDudosos());

        ArgumentCaptor<DocumentoRecibidoFoto> f = ArgumentCaptor.forClass(DocumentoRecibidoFoto.class);
        verify(fotos, times(2)).save(f.capture());
        assertEquals(1, f.getAllValues().get(0).getOrden());
        assertEquals("image/jpeg", f.getAllValues().get(0).getMime());
        assertEquals(2, f.getAllValues().get(1).getOrden());
        assertEquals("image/png", f.getAllValues().get(1).getMime());

        assertEquals(10L, c.getDocumentoId());
        assertEquals(2, c.getCantidadFotos());
        assertEquals(3, c.getCantidadRenglones());
        assertTrue(c.getRucValido());
        assertEquals(5L, c.getProveedorId());
        assertEquals(43000.0, c.getSumaRenglones());
        assertTrue(c.getTotalesCuadran());
        assertEquals(Collections.singletonList("timbrado"), c.getDudosos());
    }

    @Test
    void rucConDvInvalidoNoAsignaProveedor() throws IOException {
        when(lector.leer(anyList())).thenReturn(factura(RUC_MAL));
        CabeceraFacturaFotoMobile c = service.leer(Collections.singletonList(jpeg()), null);
        verify(compras, never()).proveedorPorRuc(any());
        assertFalse(c.getRucValido());
        assertNull(c.getProveedorId());
        assertTrue(c.getAvisos().stream().anyMatch(a -> a.contains("dígito verificador")));
    }

    @Test
    void enUnPedidoProponeElProveedorDelPedido() throws IOException {
        Pedido pedido = pedido(7L, proveedor(1L, "4444444-4"));
        when(pedidoService.findById(7L)).thenReturn(Optional.of(pedido));
        when(lector.leer(anyList())).thenReturn(factura(RUC_OK));
        CabeceraFacturaFotoMobile c = service.leer(Collections.singletonList(jpeg()), 7L);
        verify(compras).exigirEtapaAbierta(pedido);
        assertEquals(1L, c.getProveedorId());
        assertEquals(7L, c.getPedidoId());
    }

    @Test
    void masDeCincoFotosSeRechaza() throws IOException {
        List<String> seis = new ArrayList<>(Collections.nCopies(6, jpeg()));
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.leer(seis, null));
        assertTrue(e.getMessage().contains("5"));
        verify(lector, never()).leer(anyList());
    }

    @Test
    void fotoDeMasDeCuatroMegasSeRechaza() throws IOException {
        byte[] grande = new byte[4 * 1024 * 1024 + 1];
        String uri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(grande);
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.leer(Collections.singletonList(uri), null));
        assertTrue(e.getMessage().contains("4 MB"));
        verify(lector, never()).leer(anyList());
    }

    @Test
    void fotoQueNoEsImagenSeRechaza() throws IOException {
        String pdf = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(new byte[]{1, 2});
        assertThrows(GraphQLException.class, () -> service.leer(Collections.singletonList(pdf), null));
        verify(lector, never()).leer(anyList());
    }

    @Test
    void sinClaveAvisaClaro() throws IOException {
        String texto = "La lectura de facturas en papel no está configurada: pedile a un administrador que cargue la clave de OpenAI.";
        when(lector.leer(anyList())).thenThrow(new IOException(texto));
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.leer(Collections.singletonList(jpeg()), null));
        assertEquals(texto, e.getMessage());
        verify(documentos, never()).save(any());
    }

    // =================================================================================== confirmar

    @Test
    void cabeceraDuplicadaSeRechaza() {
        DocumentoRecibido doc = sinConfirmar(RUC, digito(RUC));
        when(documentos.findById(10L)).thenReturn(Optional.of(doc));
        when(proveedorService.findById(5L)).thenReturn(Optional.of(proveedor(5L, RUC_OK)));
        DocumentoRecibido otro = new DocumentoRecibido();
        otro.setId(3L);
        NotaRecepcion nota = new NotaRecepcion();
        nota.setNumero(1234);
        nota.setPedido(pedido(99L, null));
        otro.setNotaRecepcion(nota);
        when(documentos.buscarMismaFactura(RUC, 1, 12345678, "001", "002", "0001234"))
                .thenReturn(Collections.singletonList(otro));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.confirmarCabecera(input()));
        assertEquals("Esta factura ya está cargada: nota 1234 del pedido 99.", e.getMessage());
        verify(compras, never()).abrirNuevaCompra(any(), any());
    }

    @Test
    void cabeceraRepetidaContraLasNotasDelProveedorSeRechaza() {
        DocumentoRecibido doc = sinConfirmar(RUC, digito(RUC));
        when(documentos.findById(10L)).thenReturn(Optional.of(doc));
        when(proveedorService.findById(5L)).thenReturn(Optional.of(proveedor(5L, RUC_OK)));
        when(compras.avisosFacturaRepetida(any(), isNull())).thenReturn(Collections.singletonList(
                new AvisoFacturaMobile("DUPLICADA", "BLOQUEO", "Esta factura ya está cargada: la nota 1234 del pedido 8.")));
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.confirmarCabecera(input()));
        assertTrue(e.getMessage().startsWith("Esta factura ya está cargada: la nota 1234"));
        verify(compras, never()).abrirNuevaCompra(any(), any());
    }

    @Test
    void unBorradorAnteriorDeLaMismaFacturaSeLibera() {
        DocumentoRecibido doc = sinConfirmar(RUC, digito(RUC));
        when(documentos.findById(10L)).thenReturn(Optional.of(doc));
        when(proveedorService.findById(5L)).thenReturn(Optional.of(proveedor(5L, RUC_OK)));
        DocumentoRecibido borrador = sinConfirmar(RUC, digito(RUC));
        borrador.setId(4L);
        borrador.setTimbrado(12345678);
        borrador.setCabeceraConfirmada(true);
        when(documentos.buscarMismaFactura(RUC, 1, 12345678, "001", "002", "0001234"))
                .thenReturn(Arrays.asList(doc, borrador));
        service.confirmarCabecera(input());
        assertNull(borrador.getTimbrado());
        // Liberado: ya no puede crear la compra ni cargar la nota
        assertFalse(borrador.getCabeceraConfirmada());
        verify(documentos).saveAndFlush(borrador);
        verify(compras).abrirNuevaCompra(eq(doc), any());
    }

    @Test
    void numeroOTimbradoInvalidosBloquean() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sinConfirmar(RUC, digito(RUC))));
        when(proveedorService.findById(5L)).thenReturn(Optional.of(proveedor(5L, RUC_OK)));
        CabeceraFacturaFotoInput mal = input();
        mal.setNumero("12345678");
        assertThrows(GraphQLException.class, () -> service.confirmarCabecera(mal));
        CabeceraFacturaFotoInput sinTimbrado = input();
        sinTimbrado.setTimbrado(1234);
        assertThrows(GraphQLException.class, () -> service.confirmarCabecera(sinTimbrado));
        verify(compras, never()).abrirNuevaCompra(any(), any());
    }

    @Test
    void confirmarEnPedidoDeOtroProveedorSeRechaza() {
        Proveedor delPedido = proveedor(1L, "4444444-4");
        Pedido pedido = pedido(7L, delPedido);
        DocumentoRecibido doc = sinConfirmar(RUC, digito(RUC));
        doc.setPedido(pedido);
        when(documentos.findById(10L)).thenReturn(Optional.of(doc));
        when(proveedorService.findById(1L)).thenReturn(Optional.of(delPedido));
        when(compras.proveedorPorRuc(RUC)).thenReturn(proveedor(2L, RUC_OK));
        CabeceraFacturaFotoInput in = input();
        in.setProveedorId(1L);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.confirmarCabecera(in));
        assertTrue(e.getMessage().startsWith("Esta factura es del RUC " + RUC_OK + " y el pedido es de "), e.getMessage());
        verify(compras, never()).abrirEnPedido(any(), any());
    }

    @Test
    void confirmarEnPedidoConRucDudosoSigue() {
        Proveedor delPedido = proveedor(1L, "4444444-4");
        Pedido pedido = pedido(7L, delPedido);
        DocumentoRecibido doc = sinConfirmar(RUC, String.valueOf((CalcularVerificadorRuc.getDigitoVerificador(RUC, 11) + 1) % 10));
        doc.setPedido(pedido);
        when(documentos.findById(10L)).thenReturn(Optional.of(doc));
        when(proveedorService.findById(1L)).thenReturn(Optional.of(delPedido));
        FacturaProveedorMobile armada = new FacturaProveedorMobile();
        when(compras.abrirEnPedido(doc, pedido)).thenReturn(armada);
        CabeceraFacturaFotoInput in = input();
        in.setProveedorId(1L);

        assertSame(armada, service.confirmarCabecera(in));
        verify(compras).exigirEtapaAbierta(pedido);
        // El RUC leído no cerraba: queda el del proveedor del pedido
        assertEquals("4444444", doc.getRucEmisor());
        assertEquals("4", doc.getDvEmisor());
    }

    @Test
    void confirmarDelegaEnAbrirNuevaCompra() {
        DocumentoRecibido doc = sinConfirmar(RUC, digito(RUC));
        when(documentos.findById(10L)).thenReturn(Optional.of(doc));
        Proveedor proveedor = proveedor(5L, RUC_OK);
        when(proveedorService.findById(5L)).thenReturn(Optional.of(proveedor));
        FacturaProveedorMobile armada = new FacturaProveedorMobile();
        when(compras.abrirNuevaCompra(doc, proveedor)).thenReturn(armada);

        CabeceraFacturaFotoInput in = input();
        in.setEstablecimiento("1");
        in.setPuntoExpedicion("2");
        in.setNumero("1234");
        assertSame(armada, service.confirmarCabecera(in));
        assertTrue(doc.getCabeceraConfirmada());
        assertEquals("001", doc.getEstablecimiento());
        assertEquals("002", doc.getPuntoExpedicion());
        assertEquals("0001234", doc.getNumero());
        assertEquals(12345678, doc.getTimbrado());
        assertEquals(LocalDate.of(2026, 10, 20), doc.getFechaEmision().toLocalDate());
        assertEquals(2, doc.getCondicionOperacion());
        assertEquals(43000.0, doc.getTotalGeneral());
        assertSame(proveedor, doc.getProveedor());
        verify(compras, never()).abrirEnPedido(any(), any());
    }

    @Test
    void confirmarValidaRepetidosAntesDeTocarElDocumento() {
        DocumentoRecibido doc = sinConfirmar(RUC, digito(RUC));
        when(documentos.findById(10L)).thenReturn(Optional.of(doc));
        Proveedor proveedor = proveedor(5L, RUC_OK);
        when(proveedorService.findById(5L)).thenReturn(Optional.of(proveedor));
        Object[] alBuscar = new Object[4];
        doAnswer(inv -> {
            alBuscar[0] = doc.getTimbrado();
            alBuscar[1] = doc.getNumero();
            alBuscar[2] = doc.getEstablecimiento();
            alBuscar[3] = doc.getProveedor();
            return inv.callRealMethod();
        }).when(documentoRecibidoService).exigirFacturaLibre(any(), any(), any(), any(), any(), any(), any(), anyBoolean());

        service.confirmarCabecera(input());

        // Al buscar repetidos el documento seguía como lo dejó la lectura: sin la clave nueva
        assertArrayEquals(new Object[]{null, null, null, null}, alBuscar);
        InOrder orden = inOrder(documentoRecibidoService, documentos, compras);
        orden.verify(documentoRecibidoService).exigirFacturaLibre(RUC, 1, 12345678, "001", "002", "0001234", 10L, true);
        orden.verify(documentos).buscarMismaFactura(RUC, 1, 12345678, "001", "002", "0001234");
        orden.verify(compras).avisosFacturaRepetida(doc, null);
        orden.verify(documentos).save(doc);
        orden.verify(compras).abrirNuevaCompra(doc, proveedor);
    }

    @Test
    void nuevaCompraConOtroProveedorQueElDelRucSeRechaza() {
        DocumentoRecibido doc = sinConfirmar(RUC, digito(RUC));
        when(documentos.findById(10L)).thenReturn(Optional.of(doc));
        when(proveedorService.findById(6L)).thenReturn(Optional.of(proveedor(6L, "4444444-4")));
        when(compras.proveedorPorRuc(RUC)).thenReturn(proveedor(5L, RUC_OK));
        CabeceraFacturaFotoInput in = input();
        in.setProveedorId(6L);
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.confirmarCabecera(in));
        assertEquals("Esta factura es del RUC " + RUC_OK + " y el proveedor elegido es PROVEEDOR 6 (4444444-4).", e.getMessage());
        verify(documentos, never()).save(any());
        verify(compras, never()).abrirNuevaCompra(any(), any());
    }

    @Test
    void nuevaCompraConOtroProveedorDelMismoDocumentoSigue() {
        DocumentoRecibido doc = sinConfirmar(RUC, digito(RUC));
        when(documentos.findById(10L)).thenReturn(Optional.of(doc));
        Proveedor mismoDocumento = proveedor(6L, "0" + RUC_OK);
        when(proveedorService.findById(6L)).thenReturn(Optional.of(mismoDocumento));
        when(compras.proveedorPorRuc(RUC)).thenReturn(proveedor(5L, RUC_OK));
        CabeceraFacturaFotoInput in = input();
        in.setProveedorId(6L);
        service.confirmarCabecera(in);
        verify(compras).abrirNuevaCompra(doc, mismoDocumento);
    }

    @Test
    void sinProveedorNoSeConfirma() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sinConfirmar(RUC, digito(RUC))));
        CabeceraFacturaFotoInput in = input();
        in.setProveedorId(null);
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.confirmarCabecera(in));
        assertTrue(e.getMessage().contains("proveedor"));
    }

    @Test
    void fotoDevuelveElDataUri() {
        DocumentoRecibidoFoto f = new DocumentoRecibidoFoto();
        f.setOrden(2);
        f.setMime("image/png");
        f.setImagen(new byte[]{1, 2, 3});
        when(fotos.findFirstByDocumentoRecibidoIdAndOrden(10L, 2)).thenReturn(Optional.of(f));
        assertEquals("data:image/png;base64,AQID", service.foto(10L, 2));
        assertNull(service.foto(10L, 3));
        verify(seguridad, times(2)).exigirCompras();
    }

    // =================================================================================== Ayudas

    private static String digito(String ruc) {
        return String.valueOf(CalcularVerificadorRuc.getDigitoVerificador(ruc, 11));
    }

    private static String jpeg() {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(new byte[]{(byte) 0xFF, (byte) 0xD8, 1});
    }

    private static String png() {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
    }

    private static FacturaIaLeida factura(String ruc) {
        FacturaIaLeida f = new FacturaIaLeida();
        f.setEmisorRuc(ruc);
        f.setEmisorNombre("PROVEEDOR SA");
        f.setReceptorRuc("80000000-1");
        f.setTimbrado("12345678");
        f.setEstablecimiento("001");
        f.setPuntoExpedicion("002");
        f.setNumero("1234");
        f.setFechaEmision("2026-10-20");
        f.setCondicion("CONTADO");
        f.setMoneda("PYG");
        f.setTotalGeneral(new BigDecimal("43000"));
        f.getDudosos().add("timbrado");
        f.setModelo("gpt-4o");
        f.setTokensEntrada(100);
        f.setTokensSalida(50);
        f.setRespuestaCruda("{}");
        f.getItems().add(item("A1", "GALLETA", "2", "11000", "22000", 10));
        FacturaIaLeida.Item sinTotal = item(null, "JUGO", "2", "5000", null, 5);
        sinTotal.getDudosos().add("cantidad");
        f.getItems().add(sinTotal);
        f.getItems().add(item(null, "HUEVOS", "1", "11000", "11000", 0));
        return f;
    }

    private static FacturaIaLeida.Item item(String codigo, String descripcion, String cantidad, String precio, String total, int iva) {
        FacturaIaLeida.Item i = new FacturaIaLeida.Item();
        i.setCodigoProveedor(codigo);
        i.setDescripcion(descripcion);
        i.setCantidad(new BigDecimal(cantidad));
        i.setPrecioUnitario(new BigDecimal(precio));
        i.setTotalItem(total != null ? new BigDecimal(total) : null);
        i.setTasaIva(iva);
        return i;
    }

    private static Proveedor proveedor(Long id, String ruc) {
        Persona persona = new Persona();
        persona.setNombre("PROVEEDOR " + id);
        persona.setDocumento(ruc);
        Proveedor p = new Proveedor();
        p.setId(id);
        p.setPersona(persona);
        return p;
    }

    private static Pedido pedido(Long id, Proveedor proveedor) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setProveedor(proveedor);
        return p;
    }

    private static DocumentoRecibido sinConfirmar(String ruc, String dv) {
        DocumentoRecibido d = new DocumentoRecibido();
        d.setId(10L);
        d.setOrigen(DocumentoRecibido.ORIGEN_FOTO);
        d.setCabeceraConfirmada(false);
        d.setTipoDocumento(1);
        d.setRucEmisor(ruc);
        d.setDvEmisor(dv);
        return d;
    }

    private static CabeceraFacturaFotoInput input() {
        CabeceraFacturaFotoInput in = new CabeceraFacturaFotoInput();
        in.setDocumentoId(10L);
        in.setProveedorId(5L);
        in.setTimbrado(12345678);
        in.setEstablecimiento("001");
        in.setPuntoExpedicion("002");
        in.setNumero("0001234");
        in.setFechaEmision("2026-10-20");
        in.setCondicion("CREDITO");
        in.setTotalGeneral(43000.0);
        return in;
    }
}
