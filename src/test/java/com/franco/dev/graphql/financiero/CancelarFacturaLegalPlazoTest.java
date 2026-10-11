package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.enums.EstadoDE;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SIFEN solo acepta cancelar una FACTURA dentro de las 48 h de aprobada; después la única vía es una
 * nota de crédito. Antes de este cambio el método no existía: se llamaba a SIFEN igual y el usuario
 * veía un rechazo genérico sin saber qué hacer.
 */
class CancelarFacturaLegalPlazoTest {

    private static final Long FACTURA = 300L;
    private static final Long SUCURSAL = 1L;

    @Mock private DocumentoElectronicoService documentoElectronicoService;
    @Mock private FacturaLegalService service;
    @Mock private SifenEventoService sifenEventoService;
    @Mock private VentaService ventaService;
    @Mock private TesoreriaSecurityService seg;

    @InjectMocks private FacturaLegalGraphQL resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void pasadas48HorasDevuelveElCodigoQueOfreceLaNotaDeCredito() {
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                .thenReturn(Optional.of(de(LocalDateTime.now().minusHours(72))));

        String resultado = resolver.validarPlazoDeCancelacion(FACTURA, SUCURSAL);

        assertNotNull(resultado, "con 72 h la cancelación ya no se acepta");
        assertTrue(resultado.startsWith("ERROR_PLAZO_NC:"),
                "el desktop reconoce ese prefijo para ofrecer la nota de crédito: " + resultado);
    }

    @Test
    void dentroDe48HorasSeCancelaComoSiempre() {
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                .thenReturn(Optional.of(de(LocalDateTime.now().minusHours(2))));

        assertNull(resolver.validarPlazoDeCancelacion(FACTURA, SUCURSAL));
    }

    @Test
    void justoEnElLimiteTodaviaSeCancela() {
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                .thenReturn(Optional.of(de(LocalDateTime.now().minusHours(47).minusMinutes(50))));

        assertNull(resolver.validarPlazoDeCancelacion(FACTURA, SUCURSAL));
    }

    @Test
    void sinFechaDeAprobacionNoSeBloqueaNada() {
        // Sin dato no se inventa una restricción: decide SIFEN, como hasta ahora.
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                .thenReturn(Optional.of(de(null)));

        assertNull(resolver.validarPlazoDeCancelacion(FACTURA, SUCURSAL));
    }

    @Test
    void sinDocumentoElectronicoNoSeBloqueaNada() {
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                .thenReturn(Optional.empty());

        assertNull(resolver.validarPlazoDeCancelacion(FACTURA, SUCURSAL));
    }

    // ---- estado del documento electronico (filial #131) ----
    // Desde que la factura nace con activo en true, un desktop sin actualizar ofrece «Cancelar»
    // sobre electronicas no aprobadas. La guarda vive aca porque el desktop no se puede forzar.

    @Test
    void conElDocumentoSinAprobarNoSeLlegaASifen() throws Exception {
        for (EstadoDE estado : new EstadoDE[]{EstadoDE.PENDIENTE, EstadoDE.EN_LOTE, EstadoDE.RECHAZADO}) {
            FacturaLegal factura = facturaElectronica();
            when(service.findByIdAndSucursalId(FACTURA, SUCURSAL)).thenReturn(factura);
            when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                    .thenReturn(Optional.of(deEnEstado(estado)));

            String resultado = resolver.cancelarFacturaLegal(FACTURA, SUCURSAL, true);

            assertTrue(resultado.startsWith("ERROR:"), estado + " -> " + resultado);
            assertTrue(resultado.contains(estado.name()), "el mensaje dice en que estado esta: " + resultado);
            assertNull(factura.getActivo(), "la factura no se toca");
        }
        verify(sifenEventoService, never()).cancelarDE(anyString(), anyString());
        verify(ventaService, never()).cancelarVenta(any());
        verify(service, never()).save(any());
    }

    @Test
    void conElDocumentoYaCanceladoAvisaQueYaEsta() throws Exception {
        when(service.findByIdAndSucursalId(FACTURA, SUCURSAL)).thenReturn(facturaElectronica());
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                .thenReturn(Optional.of(deEnEstado(EstadoDE.CANCELADO)));

        assertEquals("ERROR: La factura ya está cancelada", resolver.cancelarFacturaLegal(FACTURA, SUCURSAL, false));
        verify(sifenEventoService, never()).cancelarDE(anyString(), anyString());
    }

    @Test
    void conElDocumentoAprobadoSeCancelaComoSiempre() throws Exception {
        FacturaLegal factura = facturaElectronica();
        when(service.findByIdAndSucursalId(FACTURA, SUCURSAL)).thenReturn(factura);
        DocumentoElectronico aprobado = deEnEstado(EstadoDE.APROBADO);
        aprobado.setFechaRecepcionSifen(LocalDateTime.now().minusHours(2));
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL)).thenReturn(Optional.of(aprobado));

        String resultado = resolver.cancelarFacturaLegal(FACTURA, SUCURSAL, false);

        assertTrue(resultado.startsWith("EXITO:"), resultado);
        verify(sifenEventoService).cancelarDE(eq(CDC), anyString());
        assertEquals(Boolean.FALSE, factura.getActivo());
    }

    @Test
    void sinDocumentoOSinEstadoNoSeBloqueaNada() {
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL)).thenReturn(Optional.empty());
        assertNull(resolver.validarEstadoParaCancelar(FACTURA, SUCURSAL));

        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL))
                .thenReturn(Optional.of(deEnEstado(null)));
        assertNull(resolver.validarEstadoParaCancelar(FACTURA, SUCURSAL));
    }

    @Test
    void laFacturaEnPapelNoMiraElDocumento() {
        // Papel con activo en NULL (las de farmacia desde fines de 2025): se cancela igual.
        FacturaLegal papel = new FacturaLegal();
        papel.setId(FACTURA);
        when(service.findByIdAndSucursalId(FACTURA, SUCURSAL)).thenReturn(papel);

        String resultado = resolver.cancelarFacturaLegal(FACTURA, SUCURSAL, false);

        assertTrue(resultado.startsWith("EXITO:"), resultado);
        assertEquals(Boolean.FALSE, papel.getActivo());
        verify(documentoElectronicoService, never()).findByFacturaLegalId(any(), any());
    }

    private static final String CDC = "01800000000000000000000000000000000000000000";

    private static FacturaLegal facturaElectronica() {
        FacturaLegal f = new FacturaLegal();
        f.setId(FACTURA);
        f.setCdc(CDC);
        return f;
    }

    private static DocumentoElectronico deEnEstado(EstadoDE estado) {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(1L);
        de.setEstado(estado);
        return de;
    }

    private static DocumentoElectronico de(LocalDateTime fechaRecepcion) {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(1L);
        de.setFechaRecepcionSifen(fechaRecepcion);
        return de;
    }
}
