package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.service.financiero.EventoCancelacionDEService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.sifen.SifenEventoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * cancelarDocumentoElectronico manda el evento de cancelacion a SIFEN, igual que cancelarFacturaLegal:
 * pide el mismo permiso. El control va antes del try del resolver, asi que el rechazo sale tal cual
 * y no envuelto como un error de cancelacion.
 */
class CancelarDocumentoElectronicoRolTest {

    private static final String CDC = "01800000000000000000000000000000000000000000";

    @Mock private TesoreriaSecurityService seg;
    @Mock private SifenEventoService sifenEventoService;
    @Mock private EventoCancelacionDEService eventoCancelacionDEService;

    @InjectMocks private EventosSifenGraphQL resolver;

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
    void sinElPermisoNoSeLlegaASifen() throws Exception {
        doThrow(new GraphQLException("No autorizado: se requiere el rol CANCELACION DE VENTA para esta acción."))
                .when(seg).requireCancelarVenta();

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.cancelarDocumentoElectronico(CDC, "motivo"));

        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        verify(sifenEventoService, never()).cancelarDE(anyString(), anyString());
    }

    @Test
    void conElPermisoSigueComoSiempre() throws Exception {
        EventoCancelacionDE evento = new EventoCancelacionDE();
        when(eventoCancelacionDEService.findActivosByCdcDocumento(CDC)).thenReturn(Collections.singletonList(evento));

        assertSame(evento, resolver.cancelarDocumentoElectronico(CDC, "motivo"));

        verify(seg).requireCancelarVenta();
        verify(sifenEventoService).cancelarDE(CDC, "motivo");
    }
}
