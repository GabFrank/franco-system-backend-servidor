package com.franco.dev.graphql.operaciones;

import com.franco.dev.graphql.operaciones.input.PagoDetalleCuotaInput;
import com.franco.dev.graphql.operaciones.input.PagoDetalleInput;
import com.franco.dev.graphql.operaciones.input.PagoInput;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.operaciones.PagoDetalleCuotaService;
import com.franco.dev.service.operaciones.PagoDetalleService;
import com.franco.dev.service.operaciones.PagoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Issue #304: los resolvers de la pantalla vieja de pagos (Pago, PagoDetalle, PagoDetalleCuota) exigen rol de
 * tesoreria: GESTIONAR para escribir y VER para leer. Sin rol rechazan antes de tocar el servicio.
 */
class PagoLegacyGraphQLSeguridadTest {

    @Mock private TesoreriaSecurityService seg;
    @Mock private PagoService pagoService;
    @Mock private PagoDetalleService pagoDetalleService;
    @Mock private PagoDetalleCuotaService pagoDetalleCuotaService;

    @InjectMocks private PagoGraphQL pagoGraphQL;
    @InjectMocks private PagoDetalleGraphQL pagoDetalleGraphQL;
    @InjectMocks private PagoDetalleCuotaGraphQL pagoDetalleCuotaGraphQL;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        doThrow(new GraphQLException("No autorizado")).when(seg).requireGestionar();
        doThrow(new GraphQLException("No autorizado")).when(seg).requireVer();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static void rechaza(Executable llamada) {
        GraphQLException e = assertThrows(GraphQLException.class, llamada);
        assertEquals("No autorizado", e.getMessage());
    }

    @Test
    void las_mutations_exigen_gestionar_sin_tocar_el_servicio() {
        PagoInput cancelar = new PagoInput();
        cancelar.setId(5L);
        rechaza(() -> pagoGraphQL.savePago(cancelar));
        rechaza(() -> pagoDetalleGraphQL.savePagoDetalle(new PagoDetalleInput()));
        rechaza(() -> pagoDetalleGraphQL.deletePagoDetalle(1L));
        rechaza(() -> pagoDetalleGraphQL.updatePagoDetalleCajaySucursal(1L, 1L, 1L));
        rechaza(() -> pagoDetalleCuotaGraphQL.savePagoDetalleCuota(new PagoDetalleCuotaInput()));
        rechaza(() -> pagoDetalleCuotaGraphQL.deletePagoDetalleCuota(1L));

        verify(seg, times(6)).requireGestionar();
        verifyNoInteractions(pagoService, pagoDetalleService, pagoDetalleCuotaService);
    }

    @Test
    void las_queries_exigen_ver_sin_tocar_el_servicio() {
        rechaza(() -> pagoGraphQL.pago(5L));
        rechaza(() -> pagoDetalleGraphQL.pagoDetalle(1L));
        rechaza(() -> pagoDetalleGraphQL.pagoDetallesPorPagoId(5L));
        rechaza(() -> pagoDetalleCuotaGraphQL.pagoDetalleCuota(1L));
        rechaza(() -> pagoDetalleCuotaGraphQL.pagoDetalleCuotas(0, 10));
        rechaza(() -> pagoDetalleCuotaGraphQL.pagoDetalleCuotasPorPagoDetalleId(1L));
        rechaza(() -> pagoDetalleCuotaGraphQL.pagoDetalleCuotasSearch("X"));
        rechaza(() -> pagoDetalleCuotaGraphQL.countPagoDetalleCuota());
        rechaza(() -> pagoDetalleCuotaGraphQL.getPagoDetalleCuotasFiltrado(null, null, null, null, false, 0, 10));

        verify(seg, times(9)).requireVer();
        verifyNoInteractions(pagoService, pagoDetalleService, pagoDetalleCuotaService);
    }

    @Test
    void con_rol_savePago_delega_en_guardarManual() {
        reset(seg);
        PagoInput in = new PagoInput();
        in.setId(5L);

        pagoGraphQL.savePago(in);

        verify(pagoService).guardarManual(5L, null, null, null, null);
    }
}
