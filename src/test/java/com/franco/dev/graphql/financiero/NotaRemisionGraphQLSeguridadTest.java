package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.enums.OrigenNotaRemision;
import com.franco.dev.graphql.financiero.input.NotaRemisionInput;
import com.franco.dev.service.financiero.KudeNotaRemisionService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.sifen.SifenEnvioSincronoService;
import com.franco.dev.service.sifen.SifenService;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturacionSecurityService;
import com.franco.dev.service.financiero.NotaRemisionPrellenadoService;
import com.franco.dev.service.financiero.NotaRemisionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * En este repo no hay @PreAuthorize y @AdminSecured está roto (issue #177): si el resolver no llama
 * al servicio de roles, cualquier usuario logueado emite notas. Cada caso comprueba que rechaza
 * **antes** de tocar el servicio.
 */
class NotaRemisionGraphQLSeguridadTest {

    @Mock private FacturacionSecurityService seg;
    @Mock private NotaRemisionService service;
    @Mock private NotaRemisionPrellenadoService prellenadoService;
    @Mock private DocumentoElectronicoService documentoElectronicoService;
    @Mock private TimbradoDetalleService timbradoDetalleService;
    @Mock private KudeNotaRemisionService kudeService;
    @Mock private com.franco.dev.service.empresarial.SucursalService sucursalService;
    @Mock private SifenService sifenService;
    @Mock private SifenEnvioSincronoService envioSincronoService;

    @InjectMocks private NotaRemisionGraphQL resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        doThrow(new GraphQLException("No autorizado")).when(seg).requireVer();
        doThrow(new GraphQLException("No autorizado")).when(seg).requireEmitir();
        doThrow(new GraphQLException("No autorizado")).when(seg).requireVerRemisionDeTransferencia();
        doThrow(new GraphQLException("No autorizado")).when(seg).requireEmitirAlgunaRemision();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void lasQueriesExigenElRolDeLectura() {
        rechaza(() -> resolver.notaRemision(1L, 1L));
        rechaza(() -> resolver.notaRemisiones(1L, null, null, 0, 10));
        rechaza(() -> resolver.notaRemisionItems(1L, 1L));
        rechaza(() -> resolver.notaRemisionPorTransferencia(5L, 1L));
        rechaza(() -> resolver.notasRemisionPorTransferencias(Collections.singletonList(5L)));
        rechaza(() -> resolver.documentoElectronicoDeNotaRemision(1L, 1L));
        rechaza(() -> resolver.imprimirNotaRemision(1L, 1L, null, false));

        verifyNoInteractions(service);
        verifyNoInteractions(documentoElectronicoService);
    }

    @Test
    void emitirYReenviarExigenElRolDeEmision() {
        rechaza(() -> resolver.generarYEnviarNotaRemision(1L, 1L));
        rechaza(() -> resolver.reenviarNotaRemision(1L, 1L));

        verifyNoInteractions(service);
        verifyNoInteractions(documentoElectronicoService);
    }

    @Test
    void elAltaDelegaLaValidacionDeRolEnElServicio() {
        // saveNotaRemision arma la entidad y llama al service, que es quien exige el rol:
        // así el control queda en un solo lugar y no se puede saltear llamando al service directo.
        doThrow(new GraphQLException("No autorizado")).when(service).crear(any(), any());

        NotaRemisionInput input = new NotaRemisionInput();
        input.setSucursalId(1L);
        input.setOrigen("MANUAL");
        input.setMotivoEmision("TRASLADO_POR_CONSIGNACION");

        rechaza(() -> resolver.saveNotaRemision(input, Collections.emptyList()));
    }

    @Test
    void anularDelegaLaValidacionDeRolEnElServicio() {
        doThrow(new GraphQLException("No autorizado")).when(service).anular(1L, 1L);

        rechaza(() -> resolver.anularNotaRemision(1L, 1L));
    }

    @Test
    void elPrellenadoExigeElRolEnSuServicio() {
        doThrow(new GraphQLException("No autorizado")).when(prellenadoService).prellenar(any(), any(), any());

        rechaza(() -> resolver.prellenarNotaRemision("MANUAL", null, 1L));
    }

    // ---- Rol NOTA REMISION EMITIR: pasa las puertas compartidas, pero solo con notas de transferencia ----

    @Test
    void elRolAcotadoNoListaNiLeeNotasSueltasNiReenviaNiAnula() {
        // Sus dos puertas se abren; requireVer y requireEmitir siguen cerradas (setUp).
        actorAcotado();
        doThrow(new GraphQLException("No autorizado")).when(service).anular(1L, 1L);

        rechaza(() -> resolver.notaRemisiones(1L, null, null, 0, 10));
        rechaza(() -> resolver.notaRemision(1L, 1L));
        rechaza(() -> resolver.notaRemisionItems(1L, 1L));
        rechaza(() -> resolver.documentoElectronicoDeNotaRemision(1L, 1L));
        rechaza(() -> resolver.reenviarNotaRemision(1L, 1L));
        rechaza(() -> resolver.anularNotaRemision(1L, 1L));
        verifyNoInteractions(envioSincronoService);
    }

    @Test
    void elRolAcotadoHaceElPrimerEnvioDeLaNotaDeUnaTransferencia() throws Exception {
        actorAcotado();
        NotaRemision nota = nota(OrigenNotaRemision.TRANSFERENCIA, true);
        DocumentoElectronico de = new DocumentoElectronico();
        when(service.findByIdAndSucursalId(1L, 1L)).thenReturn(Optional.of(nota));
        when(documentoElectronicoService.findByNotaRemisionId(1L, 1L)).thenReturn(Optional.empty());
        when(timbradoDetalleService.findByIdAndSucursalId(any(), any()))
                .thenReturn(Optional.of(new com.franco.dev.domain.financiero.TimbradoDetalle()));
        when(sifenService.crearDocumentoElectronicoNotaRemision(any(), any(), any(), any(), any())).thenReturn(de);

        resolver.generarYEnviarNotaRemision(1L, 1L);

        verify(envioSincronoService).generarYEnviarSincrono(de);
    }

    @Test
    void elRolAcotadoNoReenviaPorLaPuertaDelPrimerEnvio() throws Exception {
        // generarYEnviar con el DE ya creado es un reenvío, y sin las guardas de reenviarNotaRemision.
        actorAcotado();
        when(service.findByIdAndSucursalId(1L, 1L))
                .thenReturn(Optional.of(nota(OrigenNotaRemision.TRANSFERENCIA, true)));
        when(documentoElectronicoService.findByNotaRemisionId(1L, 1L))
                .thenReturn(Optional.of(new DocumentoElectronico()));

        noAutorizado(() -> resolver.generarYEnviarNotaRemision(1L, 1L));

        verifyNoInteractions(envioSincronoService);
        verify(sifenService, never()).crearDocumentoElectronicoNotaRemision(any(), any(), any(), any(), any());
    }

    @Test
    void elRolAcotadoNoEnviaUnaNotaAnulada() throws Exception {
        actorAcotado();
        when(service.findByIdAndSucursalId(1L, 1L))
                .thenReturn(Optional.of(nota(OrigenNotaRemision.TRANSFERENCIA, false)));
        when(documentoElectronicoService.findByNotaRemisionId(1L, 1L)).thenReturn(Optional.empty());

        noAutorizado(() -> resolver.generarYEnviarNotaRemision(1L, 1L));

        verifyNoInteractions(envioSincronoService);
        verify(sifenService, never()).crearDocumentoElectronicoNotaRemision(any(), any(), any(), any(), any());
    }

    @Test
    void elRolAcotadoNoEnviaNiImprimeUnaNotaQueNoEsDeTransferencia() throws Exception {
        actorAcotado();
        when(service.findByIdAndSucursalId(1L, 1L))
                .thenReturn(Optional.of(nota(OrigenNotaRemision.MANUAL, true)));

        GraphQLException alEnviar = noAutorizado(() -> resolver.generarYEnviarNotaRemision(1L, 1L));
        GraphQLException alImprimir = noAutorizado(() -> resolver.imprimirNotaRemision(1L, 1L, null, false));

        // «No existe» y «no es de transferencia» responden lo mismo: no sirve para sondear notas.
        GraphQLException inexistente = noAutorizado(() -> resolver.imprimirNotaRemision(2L, 1L, null, false));
        assertEquals(alImprimir.getMessage(), inexistente.getMessage());
        assertEquals(alEnviar.getMessage(), inexistente.getMessage());

        verifyNoInteractions(envioSincronoService, documentoElectronicoService, kudeService);
        verify(service, never()).findItems(any(), any());
    }

    @Test
    void elRolAcotadoImprimeLaNotaDeUnaTransferenciaAunqueSifenNoLaHayaAprobado() throws Exception {
        // Decisión de Franco (2026-10-06): la impresión no espera la aprobación de SIFEN.
        actorAcotado();
        when(service.findByIdAndSucursalId(1L, 1L))
                .thenReturn(Optional.of(nota(OrigenNotaRemision.TRANSFERENCIA, true)));
        when(documentoElectronicoService.findByNotaRemisionId(1L, 1L)).thenReturn(Optional.empty());
        when(kudeService.generarPdfBase64(any(), any(), any(), any())).thenReturn("PDF");

        assertEquals("PDF", resolver.imprimirNotaRemision(1L, 1L, null, false));
    }

    private void actorAcotado() {
        doNothing().when(seg).requireVerRemisionDeTransferencia();
        doNothing().when(seg).requireEmitirAlgunaRemision();
        when(seg.emiteSoloDesdeTransferencia()).thenReturn(true);
        when(seg.veSoloNotasDeTransferencia()).thenReturn(true);
    }

    private static NotaRemision nota(OrigenNotaRemision origen, boolean activa) {
        NotaRemision nota = new NotaRemision();
        nota.setId(1L);
        nota.setSucursalId(1L);
        nota.setOrigen(origen);
        nota.setTransferenciaId(origen == OrigenNotaRemision.TRANSFERENCIA ? 51338L : null);
        nota.setActivo(activa);
        return nota;
    }

    private static GraphQLException noAutorizado(Executable llamada) {
        GraphQLException e = assertThrows(GraphQLException.class, llamada);
        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        return e;
    }

    private static void rechaza(Executable llamada) {
        GraphQLException e = assertThrows(GraphQLException.class, llamada);
        assertEquals("No autorizado", e.getMessage());
    }
}
