package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaCredito;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.repository.financiero.NotaCreditoItemRepository;
import com.franco.dev.repository.financiero.NotaCreditoRepository;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import com.franco.dev.service.sifen.util.SerieDeNumeracionValidator;

import static org.mockito.Mockito.*;

class NotaCreditoServiceTest {

    private static final Long FACTURA = 300L;
    private static final Long SUCURSAL = 1L;
    private static final Long TIMBRADO = 10L;

    private NotaCreditoRepository repository;
    private NotaCreditoItemRepository itemRepository;
    private TimbradoDetalleRepository timbradoDetalleRepository;
    private FacturaLegalService facturaLegalService;
    private FacturaLegalItemService facturaLegalItemService;
    private DocumentoElectronicoService documentoElectronicoService;
    private FacturacionSecurityService seg;
    private SerieDeNumeracionValidator serieValidator;
    private com.franco.dev.service.empresarial.SucursalService sucursalService;
    private NotaCreditoService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotaCreditoRepository.class);
        itemRepository = mock(NotaCreditoItemRepository.class);
        timbradoDetalleRepository = mock(TimbradoDetalleRepository.class);
        facturaLegalService = mock(FacturaLegalService.class);
        facturaLegalItemService = mock(FacturaLegalItemService.class);
        documentoElectronicoService = mock(DocumentoElectronicoService.class);
        seg = mock(FacturacionSecurityService.class);
        serieValidator = mock(SerieDeNumeracionValidator.class);
        sucursalService = mock(com.franco.dev.service.empresarial.SucursalService.class);
        service = new NotaCreditoService(repository, itemRepository, timbradoDetalleRepository,
                facturaLegalService, facturaLegalItemService, documentoElectronicoService, seg,
                serieValidator, sucursalService);

        TimbradoDetalle timbrado = new TimbradoDetalle();
        timbrado.setId(TIMBRADO);
        timbrado.setActivo(true);
        when(timbradoDetalleRepository.lockById(TIMBRADO)).thenReturn(Optional.of(timbrado));
        when(repository.siguienteId()).thenReturn(700L);
        when(itemRepository.siguienteId()).thenReturn(800L);
        when(repository.save(any(NotaCredito.class))).thenAnswer(i -> i.getArgument(0));
        when(itemRepository.save(any(NotaCreditoItem.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findActivasByFactura(FACTURA, SUCURSAL)).thenReturn(Collections.emptyList());
        when(repository.findMaxNumeroByTimbradoDetalleId(TIMBRADO)).thenReturn(0);
        when(facturaLegalService.findByIdAndSucursalId(FACTURA, SUCURSAL)).thenReturn(factura(null, null));
        when(facturaLegalItemService.findByFacturaLegalId(FACTURA, SUCURSAL)).thenReturn(items());
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                .thenReturn(Optional.of(deAprobado()));
    }

    @Test
    void copiaLosTotalesDeLaFacturaSinRecalcular() {
        NotaCredito nota = crear();

        assertEquals(0, new BigDecimal("110000").compareTo(nota.getTotalFinal()));
        assertEquals(0, new BigDecimal("10000").compareTo(nota.getIvaParcial10()));
        assertEquals(0, new BigDecimal("110000").compareTo(nota.getTotalParcial10()));
        assertEquals(1, nota.getNumeroNotaCredito());
        verify(seg).requireEmitir();
    }

    @Test
    void heredaLaMonedaYElTipoDeCambioDeLaFactura() {
        when(facturaLegalService.findByIdAndSucursalId(FACTURA, SUCURSAL))
                .thenReturn(factura("USD", 7300.0));

        NotaCredito nota = crear();

        assertEquals("USD", nota.getMonedaExtranjera());
        assertEquals(0, new BigDecimal("7300").compareTo(nota.getTipoCambio()));
    }

    @Test
    void copiaElSnapshotDelReceptorDeLaFactura() {
        NotaCredito nota = crear();

        assertEquals("CLIENTE SA", nota.getNombre());
        assertEquals("80012345-6", nota.getRuc());
        assertEquals("AVDA ESPAÑA 123", nota.getDireccion());
        assertEquals(55L, nota.getClienteId());
    }

    @Test
    void losItemsSeCopianConPrecioEIva() {
        List<NotaCreditoItem> guardados = new ArrayList<>();
        when(itemRepository.save(any(NotaCreditoItem.class))).thenAnswer(i -> {
            guardados.add(i.getArgument(0));
            return i.getArgument(0);
        });

        crear();

        assertEquals(1, guardados.size());
        NotaCreditoItem item = guardados.get(0);
        assertEquals(700L, item.getNotaCreditoId());
        assertEquals(800L, item.getId());
        assertEquals(10, item.getIva());
        assertEquals(0, new BigDecimal("110000").compareTo(item.getTotal()));
        assertEquals(99L, item.getFacturaLegalItemId(), "queda la trazabilidad al ítem de la factura");
    }

    @Test
    void laFacturaSinDocumentoElectronicoNoSeAcredita() {
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL)).thenReturn(Optional.empty());

        GraphQLException e = assertThrows(GraphQLException.class, this::crear);
        assertTrue(e.getMessage().contains("no es electrónica"));
    }

    @Test
    void laFacturaNoAprobadaPorSifenNoSeAcredita() {
        DocumentoElectronico pendiente = deAprobado();
        pendiente.setEstado(EstadoDE.PENDIENTE);
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                .thenReturn(Optional.of(pendiente));

        GraphQLException e = assertThrows(GraphQLException.class, this::crear);
        assertTrue(e.getMessage().contains("no está aprobada"));
    }

    @Test
    void unaFacturaNoPuedeTenerDosNotasTotalesActivas() {
        when(repository.findActivasByFactura(FACTURA, SUCURSAL))
                .thenReturn(Collections.singletonList(new NotaCredito()));

        GraphQLException e = assertThrows(GraphQLException.class, this::crear);
        assertTrue(e.getMessage().contains("ya tiene una nota de crédito activa"));
    }

    @Test
    void laFacturaAnuladaNoSeAcredita() {
        FacturaLegal anulada = factura(null, null);
        anulada.setActivo(false);
        when(facturaLegalService.findByIdAndSucursalId(FACTURA, SUCURSAL)).thenReturn(anulada);

        assertThrows(GraphQLException.class, this::crear);
    }

    @Test
    void sinMotivoNoSeEmite() {
        assertThrows(GraphQLException.class,
                () -> service.crearDesdeFactura(FACTURA, SUCURSAL, null, null, 1L));
        verify(repository, never()).save(any());
    }

    @Test
    void anularFueraDeLaVentanaDeSifenFalla() {
        NotaCredito vieja = new NotaCredito();
        vieja.setId(1L);
        vieja.setActivo(true);
        vieja.setFecha(LocalDateTime.now().minusHours(NotaCreditoService.HORAS_PARA_ANULAR + 1));
        when(repository.findByIdAndSucursalId(1L, SUCURSAL)).thenReturn(Optional.of(vieja));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(1L, SUCURSAL));
        assertTrue(e.getMessage().contains("168"));
        assertTrue(vieja.getActivo());
    }

    private NotaCredito crear() {
        return service.crearDesdeFactura(FACTURA, SUCURSAL, MotivoEmisionNotaCredito.DEVOLUCION,
                "Devolución de mercadería", 1L);
    }

    private static FacturaLegal factura(String moneda, Double cambio) {
        FacturaLegal factura = new FacturaLegal();
        factura.setId(FACTURA);
        factura.setSucursalId(SUCURSAL);
        factura.setActivo(true);
        factura.setNombre("CLIENTE SA");
        factura.setRuc("80012345-6");
        factura.setDireccion("AVDA ESPAÑA 123");
        factura.setMonedaExtranjera(moneda);
        factura.setTipoCambio(cambio);
        factura.setIvaParcial10(10000.0);
        factura.setTotalParcial10(110000.0);
        factura.setTotalFinal(110000.0);
        factura.setCdc("01800123456001001000000122026091712345678901");

        Cliente cliente = new Cliente();
        cliente.setId(55L);
        factura.setCliente(cliente);

        TimbradoDetalle timbrado = new TimbradoDetalle();
        timbrado.setId(TIMBRADO);
        timbrado.setActivo(true);
        factura.setTimbradoDetalle(timbrado);
        return factura;
    }

    private static List<FacturaLegalItem> items() {
        FacturaLegalItem item = new FacturaLegalItem();
        item.setId(99L);
        item.setDescripcion("PRODUCTO DE PRUEBA");
        item.setCantidad(1f);
        item.setPrecioUnitario(110000.0);
        item.setTotal(110000.0);
        item.setIva(10);
        return Arrays.asList(item);
    }

    private static DocumentoElectronico deAprobado() {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(1L);
        de.setEstado(EstadoDE.APROBADO);
        de.setCdc("01800123456001001000000122026091712345678901");
        return de;
    }

    @Test
    void sinSucursalElBuscadorTraeFacturasDeTodasYCadaUnaConSuSucursal() {
        // El central corre como SERVIDOR (sucursal 0): no tiene facturas propias, busca en todas.
        FacturaLegal deOtra = factura(null, null);
        deOtra.setId(301L);
        deOtra.setSucursalId(7L);
        FacturaLegal propia = factura(null, null);
        propia.setSucursalId(SUCURSAL);
        when(facturaLegalService.buscarCandidatasANotaCredito(null, null, 0, 15))
                .thenReturn(Arrays.asList(propia, deOtra));
        when(documentoElectronicoService.findByFacturaLegalId(301L, 7L)).thenReturn(Optional.of(deAprobado()));
        when(repository.findActivasByFactura(301L, 7L)).thenReturn(Collections.emptyList());
        when(facturaLegalItemService.findByFacturaLegalId(301L, 7L)).thenReturn(items());
        com.franco.dev.domain.empresarial.Sucursal katuete = new com.franco.dev.domain.empresarial.Sucursal();
        katuete.setNombre("SUC. KATUETE 1");
        when(sucursalService.findById(7L)).thenReturn(Optional.of(katuete));
        when(sucursalService.findById(SUCURSAL)).thenReturn(Optional.empty());

        List<NotaCreditoService.FacturaParaNotaCredito> candidatas =
                service.facturasParaNotaCredito(null, null, 0, 15);

        assertEquals(2, candidatas.size());
        NotaCreditoService.FacturaParaNotaCredito otra = candidatas.get(1);
        assertEquals(7L, otra.getSucursalId(), "la nota la emite la sucursal de la factura");
        assertEquals("SUC. KATUETE 1", otra.getSucursal());
        verify(documentoElectronicoService).findByFacturaLegalId(301L, 7L);
    }
}
