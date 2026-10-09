package com.franco.dev.graphql.rrhh;

import com.franco.dev.service.rrhh.ReporteRrhhService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
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
 * Issue #346: los reportes de nomina y los recibos por id exigen rol RRHH. Sin rol rechazan
 * antes de tocar el servicio: cualquier usuario autenticado bajaba el recibo de otro
 * funcionario recorriendo ids.
 */
class ReporteRrhhGraphQLSeguridadTest {

    @Mock private RrhhSecurityService seg;
    @Mock private ReporteRrhhService service;

    @InjectMocks private ReporteRrhhGraphQL resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private void sinRol() {
        doThrow(new GraphQLException("No autorizado")).when(seg).requireVer();
    }

    private static void rechaza(Executable llamada) {
        GraphQLException e = assertThrows(GraphQLException.class, llamada);
        assertEquals("No autorizado", e.getMessage());
    }

    @Test
    void sin_rol_los_reportes_de_nomina_rechazan_sin_tocar_el_servicio() {
        sinRol();

        rechaza(() -> resolver.reporteNominaMes("2026-09", null, null));
        rechaza(() -> resolver.reporteResumenIps("2026-09"));
        rechaza(() -> resolver.reporteValesPendientes());
        rechaza(() -> resolver.reportePrestamosActivos());
        rechaza(() -> resolver.reporteAguinaldoAnual(2026));

        verify(seg, times(5)).requireVer();
        verifyNoInteractions(service);
    }

    @Test
    void sin_rol_los_recibos_por_id_rechazan_sin_tocar_el_servicio() {
        sinRol();

        rechaza(() -> resolver.imprimirReciboVale(500L, null, null));
        rechaza(() -> resolver.imprimirReciboPenalizacion(500L, null, null));
        rechaza(() -> resolver.imprimirReciboAguinaldo(500L, null, null));
        rechaza(() -> resolver.imprimirReciboPrestamo(500L, null, null));
        rechaza(() -> resolver.imprimirReciboBono(500L, null, null));
        rechaza(() -> resolver.imprimirReciboFinal(500L, null, null));

        verify(seg, times(6)).requireVer();
        verifyNoInteractions(service);
    }

    @Test
    void con_rol_los_recibos_y_reportes_se_generan() {
        when(service.reciboValeBase64(500L, 80, true)).thenReturn("vale");
        when(service.finiquitoBase64(500L, null, false)).thenReturn("finiquito");
        when(service.nominaMesBase64("2026-09", 3L, false)).thenReturn("nomina");

        assertEquals("vale", resolver.imprimirReciboVale(500L, 80, true));
        assertEquals("finiquito", resolver.imprimirReciboFinal(500L, null, null));
        assertEquals("nomina", resolver.reporteNominaMes("2026-09", 3L, false));

        verify(seg, times(3)).requireVer();
    }
}
