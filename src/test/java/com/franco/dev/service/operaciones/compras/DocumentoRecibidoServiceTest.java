package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.repository.operaciones.DocumentoRecibidoItemRepository;
import com.franco.dev.repository.operaciones.DocumentoRecibidoRepository;
import com.franco.dev.service.operaciones.ProcesoEtapaService;
import com.franco.dev.service.sifen.SifenService;
import com.roshka.sifen.core.beans.response.RespuestaConsultaDE;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Papel primero y QR después: la factura que ya entró por foto, al escanear su KuDE. */
class DocumentoRecibidoServiceTest {

    @Mock private SifenService sifenService;
    @Mock private DocumentoRecibidoRepository repository;
    @Mock private DocumentoRecibidoItemRepository itemRepository;
    @Mock private ProcesoEtapaService procesoEtapaService;
    @InjectMocks private DocumentoRecibidoService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        RespuestaConsultaDE respuesta = mock(RespuestaConsultaDE.class);
        when(respuesta.getdCodRes()).thenReturn("0422");
        when(respuesta.getdMsgRes()).thenReturn("CDC encontrado");
        when(respuesta.getRespuestaBruta()).thenReturn(FacturaSifenXmlTest.respuestaMarvi(false));
        when(sifenService.consultarDE(KudeQrTest.CDC_MARVI)).thenReturn(respuesta);
        when(repository.findByCdc(KudeQrTest.CDC_MARVI)).thenReturn(Optional.empty());
        when(repository.save(any(DocumentoRecibido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    /** La factura de MARVI (80062507, timbrado 18153001, 001-001-0067495) leída antes por foto. */
    private DocumentoRecibido porFoto(DocumentoRecibido d) {
        d.setId(3L);
        d.setOrigen(DocumentoRecibido.ORIGEN_FOTO);
        d.setTipoDocumento(1);
        d.setRucEmisor("80062507");
        d.setTimbrado(18153001);
        d.setEstablecimiento("001");
        d.setPuntoExpedicion("001");
        d.setNumero("0067495");
        when(repository.buscarMismaFactura("80062507", 1, 18153001, "001", "001", "0067495"))
                .thenReturn(Collections.singletonList(d));
        return d;
    }

    @Test
    void laFotoYaCargadaFrenaElQr() {
        DocumentoRecibido foto = porFoto(new DocumentoRecibido());
        NotaRecepcion nota = new NotaRecepcion();
        nota.setNumero(67495);
        Pedido pedido = new Pedido();
        pedido.setId(12L);
        nota.setPedido(pedido);
        foto.setNotaRecepcion(nota);
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.obtener(KudeQrTest.CDC_MARVI));
        assertEquals("Esta factura ya está cargada: nota 67495 del pedido 12.", e.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void laFotoQueEsperaOtraCompraFrenaElQr() {
        DocumentoRecibido foto = porFoto(new DocumentoRecibido());
        Pedido pedido = new Pedido();
        pedido.setId(8L);
        foto.setPedido(pedido);
        foto.setCargarAlFinalizar(true);
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.obtener(KudeQrTest.CDC_MARVI));
        assertEquals("Esta factura ya creó la compra 8: se carga sola cuando se finalice su planificación en el desktop.",
                e.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void laFotoAbandonadaSeLiberaYSigueElQr() {
        DocumentoRecibido foto = porFoto(new DocumentoRecibido());
        foto.setCabeceraConfirmada(true);
        DocumentoRecibido doc = service.obtener(KudeQrTest.CDC_MARVI);
        assertNull(foto.getTimbrado());
        assertFalse(foto.getCabeceraConfirmada());
        assertEquals(KudeQrTest.CDC_MARVI, doc.getCdc());
        assertEquals(18153001, doc.getTimbrado());
        InOrder orden = inOrder(repository);
        orden.verify(repository).saveAndFlush(foto);
        orden.verify(repository).save(doc);
        verify(itemRepository, times(2)).save(any(DocumentoRecibidoItem.class));
    }

    @Test
    void sinOtraFacturaElQrSigueComoSiempre() {
        DocumentoRecibido doc = service.obtener(KudeQrTest.CDC_MARVI);
        assertEquals("80062507", doc.getRucEmisor());
        verify(repository, never()).saveAndFlush(any());
        verify(repository).save(doc);
    }
}
