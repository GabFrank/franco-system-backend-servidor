package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.graphql.operaciones.input.SolicitudPagoDetalleInput;
import com.franco.dev.repository.operaciones.SolicitudPagoDetalleRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.operaciones.SolicitudPagoDetalleService;
import com.franco.dev.service.operaciones.SolicitudPagoNotaRecepcionService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Issue #306: por el resolver de compras, una obligacion de RRHH solo se lee con rol de tesoreria o RRHH y ninguna
 * mutation toca solicitudes que no sean de compra. Compras sigue sin rol.
 */
class SolicitudPagoGraphQLTipoTest {

    private static final long COMPRA = 1L;
    private static final long GASTO = 2L;
    private static final long RRHH = 3L;
    private static final long INEXISTENTE = 99L;

    @Mock private SolicitudPagoService solicitudPagoService;
    @Mock private SolicitudPagoNotaRecepcionService notaService;
    @Mock private SolicitudPagoDetalleService detalleService;
    @Mock private TesoreriaSecurityService tesoreriaSecurityService;
    @Mock private RrhhSecurityService rrhhSecurityService;
    @Mock private SolicitudPagoRepository solicitudRepo;
    @Mock private SolicitudPagoDetalleRepository detalleRepo;

    @InjectMocks private SolicitudPagoGraphQL resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(solicitudPagoService.getRepository()).thenReturn(solicitudRepo);
        when(detalleService.getRepository()).thenReturn(detalleRepo);
        when(solicitudRepo.findTipoById(COMPRA)).thenReturn(Optional.of(TipoSolicitudPago.COMPRA));
        when(solicitudRepo.findTipoById(GASTO)).thenReturn(Optional.of(TipoSolicitudPago.GASTO));
        when(solicitudRepo.findTipoById(RRHH)).thenReturn(Optional.of(TipoSolicitudPago.RRHH));
        when(solicitudRepo.findTipoById(INEXISTENTE)).thenReturn(Optional.empty());
        when(solicitudPagoService.findById(anyLong())).thenAnswer(inv -> Optional.of(solicitud(inv.getArgument(0))));
        sinRoles();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static SolicitudPago solicitud(long id) {
        SolicitudPago s = new SolicitudPago();
        s.setId(id);
        return s;
    }

    private void sinRoles() {
        when(tesoreriaSecurityService.hasAnyRole(TesoreriaSecurityService.TODOS)).thenReturn(false);
        when(rrhhSecurityService.hasAnyRole(RrhhSecurityService.TODOS)).thenReturn(false);
    }

    private static void rechaza(Executable llamada, String mensaje) {
        GraphQLException e = assertThrows(GraphQLException.class, llamada);
        assertTrue(e.getMessage().contains(mensaje), e.getMessage());
        // El rechazo no revela el tipo de la solicitud.
        assertFalse(e.getMessage().contains("RRHH") || e.getMessage().contains("GASTO"), e.getMessage());
    }

    // ── Lectura ──

    @Test
    void una_obligacion_rrhh_no_se_lee_sin_rol() {
        rechaza(() -> resolver.solicitudPago(RRHH), "No autorizado para ver la solicitud #3");
        rechaza(() -> resolver.notasAsociadasASolicitud(RRHH), "No autorizado para ver la solicitud #3");
        rechaza(() -> resolver.imprimirSolicitudPagoPDF(RRHH), "No autorizado para ver la solicitud #3");
        rechaza(() -> resolver.imprimirSolicitudPagoTicket(RRHH, "X", null), "No autorizado para ver la solicitud #3");

        verify(solicitudPagoService, never()).findById(anyLong());
        verify(solicitudPagoService, never()).getNotasAsociadas(anyLong());
        verify(solicitudRepo, never()).findByIdWithUsuarioAndMoneda(anyLong());
    }

    @Test
    void una_obligacion_rrhh_se_lee_con_rol_de_rrhh() {
        when(rrhhSecurityService.hasAnyRole(RrhhSecurityService.TODOS)).thenReturn(true);

        assertEquals(RRHH, resolver.solicitudPago(RRHH).getId());
    }

    @Test
    void una_obligacion_rrhh_se_lee_con_rol_de_tesoreria_o_admin() {
        // hasAnyRole resuelve el bypass ADMIN dentro del servicio de seguridad.
        when(tesoreriaSecurityService.hasAnyRole(TesoreriaSecurityService.TODOS)).thenReturn(true);

        assertEquals(RRHH, resolver.solicitudPago(RRHH).getId());
    }

    @Test
    void compras_y_gastos_se_leen_sin_rol() {
        assertEquals(COMPRA, resolver.solicitudPago(COMPRA).getId());
        assertEquals(GASTO, resolver.solicitudPago(GASTO).getId());
        resolver.notasAsociadasASolicitud(COMPRA);

        verify(solicitudPagoService).getNotasAsociadas(COMPRA);
        verify(tesoreriaSecurityService, never()).hasAnyRole(TesoreriaSecurityService.TODOS);
    }

    @Test
    void un_id_inexistente_sigue_el_flujo_de_siempre() {
        when(solicitudPagoService.findById(INEXISTENTE)).thenReturn(Optional.empty());

        assertNull(resolver.solicitudPago(INEXISTENTE));
    }

    // ── Escritura ──

    private void todasLasMutationsRechazan(long id) {
        SolicitudPagoGraphQL.SolicitudPagoInput in = new SolicitudPagoGraphQL.SolicitudPagoInput();
        in.setId(id);
        String msg = "La solicitud #" + id + " no es de compras: se gestiona desde su propio módulo.";
        rechaza(() -> resolver.actualizarSolicitudPago(in), msg);
        rechaza(() -> resolver.deleteSolicitudPago(id), msg);
        rechaza(() -> resolver.actualizarEstadoSolicitudPago(id, SolicitudPagoEstado.SOLICITADO), msg);
        rechaza(() -> resolver.agregarNotaASolicitudPago(id, 10L, 1000.0), msg);
        rechaza(() -> resolver.removerNotaDeSolicitudPago(id, 10L), msg);
        rechaza(() -> resolver.agregarSolicitudPagoDetalle(id, new SolicitudPagoDetalleInput()), msg);
        when(detalleRepo.findSolicitudIdById(50L)).thenReturn(Optional.of(id));
        rechaza(() -> resolver.eliminarSolicitudPagoDetalle(50L), msg);
    }

    @Test
    void las_mutations_de_compras_no_tocan_una_obligacion_rrhh() {
        todasLasMutationsRechazan(RRHH);

        verify(solicitudPagoService, never()).actualizarSolicitudPago(anyLong(), any(), any(), any(), any(), any());
        verify(solicitudPagoService, never()).eliminarSolicitud(anyLong());
        verify(solicitudPagoService, never()).actualizarEstado(anyLong(), any());
        verifyNoInteractions(notaService);
        verify(detalleService, never()).deleteById(anyLong());
        verify(detalleService, never()).agregarDetalle(any(), any());
    }

    @Test
    void las_mutations_de_compras_no_tocan_un_gasto() {
        todasLasMutationsRechazan(GASTO);

        verify(solicitudPagoService, never()).eliminarSolicitud(anyLong());
        verify(solicitudPagoService, never()).actualizarEstado(anyLong(), any());
        verifyNoInteractions(notaService);
        verify(detalleService, never()).deleteById(anyLong());
    }

    @Test
    void las_mutations_sobre_una_compra_delegan_como_siempre() {
        resolver.deleteSolicitudPago(COMPRA);
        resolver.actualizarEstadoSolicitudPago(COMPRA, SolicitudPagoEstado.SOLICITADO);
        resolver.removerNotaDeSolicitudPago(COMPRA, 10L);
        when(detalleRepo.findSolicitudIdById(50L)).thenReturn(Optional.of(COMPRA));
        resolver.eliminarSolicitudPagoDetalle(50L);

        verify(solicitudPagoService).eliminarSolicitud(COMPRA);
        verify(solicitudPagoService).actualizarEstado(COMPRA, SolicitudPagoEstado.SOLICITADO);
        verify(notaService).removerNotaDeSolicitud(COMPRA, 10L);
        verify(detalleService).deleteById(50L);
    }

    @Test
    void eliminar_un_detalle_inexistente_delega_como_siempre() {
        when(detalleRepo.findSolicitudIdById(77L)).thenReturn(Optional.empty());

        resolver.eliminarSolicitudPagoDetalle(77L);

        verify(detalleService).deleteById(77L);
    }
}
