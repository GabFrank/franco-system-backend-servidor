package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturacionSecurityService;
import com.franco.dev.service.financiero.NotaRemisionService;
import com.franco.dev.service.sifen.SifenEventoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Anular una nota aprobada tiene que cancelarla ANTE SIFEN, no solo darla de baja en la base.
 *
 * Antes solo hacía la baja lógica, y la nota de remisión 001-001-0000009 emitida en producción el
 * 2026-09-18 quedó aprobada y vigente ante la SET mientras el sistema la mostraba anulada. Estos
 * tests existen para que esa divergencia no vuelva.
 */
class NotaRemisionAnulacionTest {

    private static final String CDC = "07800994825001001000000922026091812480063589";

    @Mock private FacturacionSecurityService seg;
    @Mock private NotaRemisionService service;
    @Mock private DocumentoElectronicoService documentoElectronicoService;
    @Mock private SifenEventoService sifenEventoService;

    @InjectMocks private NotaRemisionGraphQL resolver;

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
    void anularUnaNotaAprobadaLaCancelaEnSifen() throws Exception {
        when(documentoElectronicoService.findByNotaRemisionId(10L, 1L))
                .thenReturn(Optional.of(de(EstadoDE.APROBADO)));
        when(service.anular(10L, 1L)).thenReturn(new NotaRemision());

        resolver.anularNotaRemision(10L, 1L);

        verify(sifenEventoService).cancelarDE(eq(CDC), anyString());
        verify(service).anular(10L, 1L);
    }

    @Test
    void siSifenRechazaLaCancelacion_laNotaNoSeDaDeBaja() throws Exception {
        // Es el caso que dejó el documento vivo: si la SET no lo cancela, sigue siendo válido y
        // el sistema no puede fingir lo contrario.
        when(documentoElectronicoService.findByNotaRemisionId(10L, 1L))
                .thenReturn(Optional.of(de(EstadoDE.APROBADO)));
        doThrow(new IllegalStateException("Plazo de solicitud de cancelación extemporáneo"))
                .when(sifenEventoService).cancelarDE(anyString(), anyString());

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.anularNotaRemision(10L, 1L));

        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("sigue vigente"), e.getMessage());
        verify(service, never()).anular(anyLong(), anyLong());
    }

    @Test
    void sinDocumentoAprobadoLaBajaEsSoloLocal() throws Exception {
        // Una nota que nunca se envió, o que SIFEN rechazó, no tiene nada que cancelar.
        when(documentoElectronicoService.findByNotaRemisionId(10L, 1L))
                .thenReturn(Optional.of(de(EstadoDE.RECHAZADO)));
        when(service.anular(10L, 1L)).thenReturn(new NotaRemision());

        resolver.anularNotaRemision(10L, 1L);

        verify(sifenEventoService, never()).cancelarDE(anyString(), anyString());
        verify(service).anular(10L, 1L);
    }

    private static DocumentoElectronico de(EstadoDE estado) {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setCdc(CDC);
        de.setEstado(estado);
        return de;
    }
}
