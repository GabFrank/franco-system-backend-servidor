package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.NotaCredito;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturacionSecurityService;
import com.franco.dev.service.financiero.NotaCreditoService;
import com.franco.dev.service.financiero.NotaRemisionService;
import com.franco.dev.service.sifen.SifenEnvioSincronoService;
import com.franco.dev.service.sifen.SifenEventoService;
import com.franco.dev.service.sifen.SifenService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Una nota EN_LOTE sin respuesta de SIFEN: reenviarla armaba un segundo lote con el mismo CDC, y
 * anularla la daba de baja solo en el sistema aunque SIFEN la terminara aprobando. Visto en bodega
 * el 2026-09-30, con las notas trabadas EN_LOTE porque nadie consultaba su lote.
 */
class NotasEnLoteReenvioAnulacionTest {

    private static final String CDC = "05800994825020001000000122026093012766532867";

    @Mock private FacturacionSecurityService seg;
    @Mock private NotaCreditoService ncService;
    @Mock private NotaRemisionService nrService;
    @Mock private DocumentoElectronicoService documentoElectronicoService;
    @Mock private SifenService sifenService;
    @Mock private SifenEventoService sifenEventoService;
    @Mock private SifenEnvioSincronoService envioSincronoService;

    @InjectMocks private NotaCreditoGraphQL ncResolver;
    @InjectMocks private NotaRemisionGraphQL nrResolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        // Los dos resolvers tienen el service en un campo llamado igual: se inyecta a mano.
        org.springframework.test.util.ReflectionTestUtils.setField(ncResolver, "service", ncService);
        org.springframework.test.util.ReflectionTestUtils.setField(nrResolver, "service", nrService);
        when(ncService.anular(anyLong(), anyLong())).thenReturn(new NotaCredito());
        when(nrService.anular(anyLong(), anyLong())).thenReturn(new NotaRemision());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void ncEnProcesoNoSeReenvia() throws Exception {
        enLote(true);

        GraphQLException e = assertThrows(GraphQLException.class, () -> ncResolver.reenviarNotaCredito(10L, 20L));

        assertTrue(e.getMessage().contains("procesando"), e.getMessage());
        verify(envioSincronoService).refrescarSiEnLote(any());
        verify(envioSincronoService, never()).generarYEnviarSincrono(any());
    }

    @Test
    void nrEnProcesoNoSeReenvia() throws Exception {
        enLote(true);

        assertThrows(GraphQLException.class, () -> nrResolver.reenviarNotaRemision(10L, 20L));

        verify(envioSincronoService, never()).generarYEnviarSincrono(any());
    }

    @Test
    void ncConElLoteMuertoSeReenvia() throws Exception {
        enLote(false);

        ncResolver.reenviarNotaCredito(10L, 20L);

        verify(envioSincronoService).generarYEnviarSincrono(any());
    }

    @Test
    void ncQueSaleAprobadaAlConsultarNoSeReenvia() throws Exception {
        when(documentoElectronicoService.findByNotaCreditoId(10L, 20L))
                .thenReturn(Optional.of(de(EstadoDE.EN_LOTE)))
                .thenReturn(Optional.of(de(EstadoDE.APROBADO)));

        GraphQLException e = assertThrows(GraphQLException.class, () -> ncResolver.reenviarNotaCredito(10L, 20L));

        assertTrue(e.getMessage().contains("aprobada"), e.getMessage());
        verify(envioSincronoService, never()).generarYEnviarSincrono(any());
    }

    @Test
    void ncEnProcesoNoSeAnula() throws Exception {
        enLote(true);

        assertThrows(GraphQLException.class, () -> ncResolver.anularNotaCredito(10L, 20L));

        verify(ncService, never()).anular(anyLong(), anyLong());
    }

    @Test
    void nrEnProcesoNoSeAnula() throws Exception {
        enLote(true);

        assertThrows(GraphQLException.class, () -> nrResolver.anularNotaRemision(10L, 20L));

        verify(nrService, never()).anular(anyLong(), anyLong());
    }

    @Test
    void nrQueSaleAprobadaAlConsultarSeCancelaEnSifen() throws Exception {
        when(documentoElectronicoService.findByNotaRemisionId(10L, 20L))
                .thenReturn(Optional.of(de(EstadoDE.EN_LOTE)))
                .thenReturn(Optional.of(de(EstadoDE.APROBADO)));

        nrResolver.anularNotaRemision(10L, 20L);

        verify(sifenEventoService).cancelarDE(eq(CDC), anyString());
        verify(nrService).anular(10L, 20L);
    }

    @Test
    void ncConElLoteMuertoSeAnulaSoloLocal() throws Exception {
        enLote(false);

        ncResolver.anularNotaCredito(10L, 20L);

        verify(sifenEventoService, never()).cancelarDE(anyString(), anyString());
        verify(ncService).anular(10L, 20L);
    }

    private void enLote(boolean sigueEnProceso) {
        when(documentoElectronicoService.findByNotaCreditoId(10L, 20L)).thenReturn(Optional.of(de(EstadoDE.EN_LOTE)));
        when(documentoElectronicoService.findByNotaRemisionId(10L, 20L)).thenReturn(Optional.of(de(EstadoDE.EN_LOTE)));
        when(envioSincronoService.sigueEnProceso(any())).thenReturn(sigueEnProceso);
    }

    private static DocumentoElectronico de(EstadoDE estado) {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(1L);
        de.setSucursalId(20L);
        de.setCdc(CDC);
        de.setEstado(estado);
        return de;
    }
}
