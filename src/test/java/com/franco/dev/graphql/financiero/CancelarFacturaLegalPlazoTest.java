package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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

    private static DocumentoElectronico de(LocalDateTime fechaRecepcion) {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(1L);
        de.setFechaRecepcionSifen(fechaRecepcion);
        return de;
    }
}
