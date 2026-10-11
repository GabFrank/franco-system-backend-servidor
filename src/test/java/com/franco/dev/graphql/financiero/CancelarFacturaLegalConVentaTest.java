package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.sifen.SifenEventoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * cancelarFacturaLegal con «cancelar tambien la venta».
 *
 * <p>Dos cosas que antes pasaban: el evento de cancelacion se mandaba dos veces (una aca y otra desde
 * cancelarVenta), y si la venta ya estaba cancelada, cancelarVenta —que alterna— la reactivaba.
 */
class CancelarFacturaLegalConVentaTest {

    private static final Long FACTURA = 300L;
    private static final Long SUCURSAL = 1L;
    private static final String CDC = "01800000000000000000000000000000000000000000";

    @Mock private DocumentoElectronicoService documentoElectronicoService;
    @Mock private FacturaLegalService service;
    @Mock private SifenEventoService sifenEventoService;
    @Mock private VentaService ventaService;
    @Mock private TesoreriaSecurityService seg;

    @InjectMocks private FacturaLegalGraphQL resolver;

    private AutoCloseable mocks;
    private Venta venta;
    private FacturaLegal factura;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        venta = new Venta();
        venta.setId(500L);
        venta.setEstado(VentaEstado.CONCLUIDA);
        factura = new FacturaLegal();
        factura.setId(FACTURA);
        factura.setVenta(venta);
        when(service.findByIdAndSucursalId(FACTURA, SUCURSAL)).thenReturn(factura);
        when(ventaService.cancelarVenta(any(), anyBoolean())).thenReturn(true);
        when(ventaService.cancelarVenta(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private void electronicaAprobada() {
        factura.setCdc(CDC);
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(1L);
        de.setEstado(EstadoDE.APROBADO);
        de.setFechaRecepcionSifen(LocalDateTime.now().minusHours(2));
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL)).thenReturn(Optional.of(de));
    }

    @Test
    void elEventoSeMandaUnaSolaVezYLaVentaSeCancelaAvisandoQueYaEsta() throws Exception {
        electronicaAprobada();

        String resultado = resolver.cancelarFacturaLegal(FACTURA, SUCURSAL, true);

        assertTrue(resultado.startsWith("EXITO:"), resultado);
        verify(sifenEventoService, times(1)).cancelarDE(anyString(), anyString());
        verify(ventaService).cancelarVenta(venta, true);
        verify(ventaService, never()).cancelarVenta(venta);
    }

    @Test
    void siLaVentaYaEstabaCanceladaNoSeLaReactiva() throws Exception {
        electronicaAprobada();
        venta.setEstado(VentaEstado.CANCELADA);

        String resultado = resolver.cancelarFacturaLegal(FACTURA, SUCURSAL, true);

        assertEquals("EXITO: Factura electrónica cancelada (la venta ya estaba cancelada)", resultado);
        verify(sifenEventoService).cancelarDE(anyString(), anyString());
        verify(ventaService, never()).cancelarVenta(any(), anyBoolean());
        verify(ventaService, never()).cancelarVenta(any());
        assertEquals(Boolean.FALSE, factura.getActivo());
        verify(service).save(factura);
    }

    @Test
    void enPapelConLaVentaYaCanceladaTampocoSeLaReactiva() {
        venta.setEstado(VentaEstado.CANCELADA);

        String resultado = resolver.cancelarFacturaLegal(FACTURA, SUCURSAL, true);

        assertEquals("EXITO: Factura cancelada (la venta ya estaba cancelada)", resultado);
        verify(ventaService, never()).cancelarVenta(any(), anyBoolean());
        verify(ventaService, never()).cancelarVenta(any());
        assertEquals(Boolean.FALSE, factura.getActivo());
    }

    @Test
    void enPapelConLaVentaVigenteSeCancelanLasDos() {
        String resultado = resolver.cancelarFacturaLegal(FACTURA, SUCURSAL, true);

        assertEquals("EXITO: Factura cancelada y venta cancelada", resultado);
        verify(ventaService).cancelarVenta(venta);
        assertEquals(Boolean.FALSE, factura.getActivo());
    }

    @Test
    void soloLaFacturaConLaVentaVigenteDiceQueLaVentaSigue() {
        assertEquals("EXITO: Factura cancelada (venta mantiene activa)",
                resolver.cancelarFacturaLegal(FACTURA, SUCURSAL, false));
        verify(ventaService, never()).cancelarVenta(any(), anyBoolean());
        verify(ventaService, never()).cancelarVenta(any());
    }
}
