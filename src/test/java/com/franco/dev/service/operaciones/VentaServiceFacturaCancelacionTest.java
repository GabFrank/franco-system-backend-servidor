package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.financiero.VentaTarjetaService;
import com.franco.dev.service.operaciones.VentaService.BajaFactura;
import com.franco.dev.service.sifen.SifenEventoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Que le pasa a la factura legal cuando su venta se cancela.
 *
 * <p>Antes {@code cancelarVenta} la marcaba inactiva siempre: con el documento todavia sin aprobar, o
 * con SIFEN rechazando el evento, quedaba una factura vigente en SIFEN que el sistema mostraba
 * cancelada (#408). Ahora la venta se cancela igual y la factura queda inactiva solo si fiscalmente
 * dejo de valer.
 */
class VentaServiceFacturaCancelacionTest {

    private static final Long VENTA = 500L;
    private static final Long FACTURA = 300L;
    private static final Long SUCURSAL = 1L;
    private static final String CDC = "01800000000000000000000000000000000000000000";

    @Mock private FacturaLegalService facturaLegalService;
    @Mock private DocumentoElectronicoService documentoElectronicoService;
    @Mock private SifenEventoService sifenEventoService;
    // Los que recorre cancelarVenta antes de llegar a la factura: sin stub devuelven vacio.
    @Mock private MovimientoCajaService movimientoCajaService;
    @Mock private VentaItemService ventaItemService;
    @Mock private VentaTarjetaService ventaTarjetaService;
    @Mock private VentaCreditoService ventaCreditoService;

    @InjectMocks private VentaService service;

    private AutoCloseable mocks;
    private Venta venta;
    private FacturaLegal factura;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        venta = new Venta();
        venta.setId(VENTA);
        venta.setSucursalId(SUCURSAL);
        factura = new FacturaLegal();
        factura.setId(FACTURA);
        factura.setSucursalId(SUCURSAL);
        when(facturaLegalService.findByVentaIdAndSucursalId(VENTA, SUCURSAL)).thenReturn(factura);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private void electronicaConDocumentoEn(EstadoDE estado) {
        factura.setCdc(CDC);
        DocumentoElectronico de = new DocumentoElectronico();
        de.setEstado(estado);
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL)).thenReturn(Optional.of(de));
    }

    // ---- la regla ----

    @Test
    void laReglaDeBaja() {
        // Papel: no hay nada en SIFEN.
        assertEquals(BajaFactura.INACTIVAR, VentaService.bajaDeFactura(false, null, false));
        // Electronica: manda el documento.
        assertEquals(BajaFactura.INACTIVAR, VentaService.bajaDeFactura(true, EstadoDE.CANCELADO, false));
        assertEquals(BajaFactura.INACTIVAR, VentaService.bajaDeFactura(true, EstadoDE.RECHAZADO, false));
        assertEquals(BajaFactura.CANCELAR_EN_SIFEN, VentaService.bajaDeFactura(true, EstadoDE.APROBADO, false));
        assertEquals(BajaFactura.DEJAR_ACTIVA, VentaService.bajaDeFactura(true, EstadoDE.PENDIENTE, false));
        assertEquals(BajaFactura.DEJAR_ACTIVA, VentaService.bajaDeFactura(true, EstadoDE.EN_LOTE, false));
        assertEquals(BajaFactura.DEJAR_ACTIVA, VentaService.bajaDeFactura(true, null, false));
        // Quien llama ya la cancelo en SIFEN: no importa en que estado se vea el documento todavia.
        assertEquals(BajaFactura.INACTIVAR, VentaService.bajaDeFactura(true, EstadoDE.APROBADO, true));
    }

    // ---- el flujo ----

    @Test
    void facturaEnPapelQuedaInactivaSinMirarSifen() throws Exception {
        service.darDeBajaFactura(venta, false);

        assertEquals(Boolean.FALSE, factura.getActivo());
        verify(facturaLegalService).save(factura);
        verify(documentoElectronicoService, never()).findByFacturaLegalId(any(), any());
        verify(sifenEventoService, never()).cancelarDE(anyString(), anyString());
    }

    @Test
    void conElDocumentoSinAprobarLaFacturaQuedaActivaYNoSeMandaNada() throws Exception {
        for (EstadoDE estado : new EstadoDE[]{EstadoDE.PENDIENTE, EstadoDE.EN_LOTE}) {
            electronicaConDocumentoEn(estado);

            service.darDeBajaFactura(venta, false);

            assertNull(factura.getActivo(), estado + ": SIFEN la puede aprobar despues, sigue vigente");
        }
        verify(sifenEventoService, never()).cancelarDE(anyString(), anyString());
        verify(facturaLegalService, never()).save(any());
    }

    @Test
    void aprobadaYSifenAceptaElEventoQuedaInactiva() throws Exception {
        electronicaConDocumentoEn(EstadoDE.APROBADO);

        service.darDeBajaFactura(venta, false);

        verify(sifenEventoService).cancelarDE(CDC, "Cancelación de venta");
        assertEquals(Boolean.FALSE, factura.getActivo());
        verify(facturaLegalService).save(factura);
    }

    @Test
    void aprobadaYSifenNoAceptaElEventoQuedaActiva() throws Exception {
        electronicaConDocumentoEn(EstadoDE.APROBADO);
        doThrow(new IllegalStateException("Evento rechazado por SIFEN")).when(sifenEventoService)
                .cancelarDE(CDC, "Cancelación de venta");

        service.darDeBajaFactura(venta, false);   // no lanza: la venta se cancela igual

        assertNull(factura.getActivo(), "sigue vigente en SIFEN: corresponde nota de credito");
        verify(facturaLegalService, never()).save(any());
    }

    @Test
    void rechazadaOYaCanceladaQuedaInactivaSinMandarNada() throws Exception {
        for (EstadoDE estado : new EstadoDE[]{EstadoDE.RECHAZADO, EstadoDE.CANCELADO}) {
            factura.setActivo(null);
            electronicaConDocumentoEn(estado);

            service.darDeBajaFactura(venta, false);

            assertEquals(Boolean.FALSE, factura.getActivo(), estado.name());
        }
        verify(sifenEventoService, never()).cancelarDE(anyString(), anyString());
    }

    @Test
    void siQuienLlamaYaLaCanceloEnSifenNoSeReenvia() throws Exception {
        electronicaConDocumentoEn(EstadoDE.APROBADO);   // el evento quedo pendiente: todavia se ve aprobado

        service.darDeBajaFactura(venta, true);

        verify(sifenEventoService, never()).cancelarDE(anyString(), anyString());
        assertEquals(Boolean.FALSE, factura.getActivo());
    }

    @Test
    void siNoSePuedeLeerElDocumentoLaFacturaQuedaActivaYLaVentaSigue() throws Exception {
        factura.setCdc(CDC);
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                .thenThrow(new RuntimeException("mas de un documento"));

        service.darDeBajaFactura(venta, false);   // no lanza

        assertNull(factura.getActivo());
        verify(sifenEventoService, never()).cancelarDE(anyString(), anyString());
    }

    @Test
    void sinFacturaNoHaceNada() {
        when(facturaLegalService.findByVentaIdAndSucursalId(VENTA, SUCURSAL)).thenReturn(null);

        service.darDeBajaFactura(venta, false);

        verify(facturaLegalService, never()).save(any());
    }

    // ---- cancelar o reactivar ----

    private VentaService ventaQueSeGuarda(VentaEstado estadoActual) {
        venta.setEstado(estadoActual);
        Cobro cobro = new Cobro();
        cobro.setId(9L);
        venta.setCobro(cobro);
        VentaService espia = spy(service);
        doReturn(venta).when(espia).save(venta);
        doNothing().when(espia).darDeBajaFactura(any(), anyBoolean());
        return espia;
    }

    @Test
    void alCancelarLaVentaSeDaDeBajaLaFactura() {
        VentaService espia = ventaQueSeGuarda(VentaEstado.CONCLUIDA);

        assertEquals(Boolean.TRUE, espia.cancelarVenta(venta));

        assertEquals(VentaEstado.CANCELADA, venta.getEstado());
        verify(espia).darDeBajaFactura(venta, false);
    }

    @Test
    void alReactivarLaVentaNoSeTocaLaFactura() {
        // cancelarVenta alterna. Antes, reactivar volvia a mandar el evento de cancelacion a SIFEN.
        VentaService espia = ventaQueSeGuarda(VentaEstado.CANCELADA);

        assertEquals(Boolean.TRUE, espia.cancelarVenta(venta));

        assertEquals(VentaEstado.CONCLUIDA, venta.getEstado());
        verify(espia, never()).darDeBajaFactura(any(), anyBoolean());
    }

    @Test
    void elAvisoDeQueYaSeCanceloEnSifenLlegaALaBaja() {
        VentaService espia = ventaQueSeGuarda(VentaEstado.CONCLUIDA);

        espia.cancelarVenta(venta, true);

        verify(espia).darDeBajaFactura(venta, true);
    }
}
