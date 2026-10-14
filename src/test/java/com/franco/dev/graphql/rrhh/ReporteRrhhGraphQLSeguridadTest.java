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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Issue #346: los reportes de nomina y los recibos por id exigen rol RRHH. Sin rol rechazan
 * antes de tocar el servicio: cualquier usuario autenticado bajaba el recibo de otro
 * funcionario recorriendo ids.
 */
class ReporteRrhhGraphQLSeguridadTest {

    @Mock private RrhhSecurityService seg;
    @Mock private com.franco.dev.service.rrhh.RrhhAlcanceService alcance;
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
        // Los recibos de finiquito y de ítems pasan por el alcance (rol acotado RRHH SUCURSAL).
        doThrow(new GraphQLException("No autorizado")).when(alcance).requireVerFiniquito(org.mockito.ArgumentMatchers.any());
        doThrow(new GraphQLException("No autorizado")).when(alcance).requireVerItemDeLiquidacion(org.mockito.ArgumentMatchers.any());
        doThrow(new GraphQLException("No autorizado")).when(alcance).requireVerItemDeFiniquito(org.mockito.ArgumentMatchers.any());
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

        // El de finiquito pasa por el alcance, que acepta además al rol acotado dentro de sus sucursales.
        verify(seg, times(5)).requireVer();
        verify(alcance).requireVerFiniquito(500L);
        verifyNoInteractions(service);
    }

    /**
     * Recorre el resolver entero: un recibo o reporte que se agregue sin control de rol hace
     * fallar este test aunque nadie lo sume a las listas de arriba.
     */
    @Test
    void sin_rol_ningun_metodo_publico_del_resolver_llega_al_servicio() throws Exception {
        sinRol();
        int publicos = 0;

        for (Method m : ReporteRrhhGraphQL.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())) continue;
            publicos++;
            InvocationTargetException e = assertThrows(InvocationTargetException.class,
                    () -> m.invoke(resolver, new Object[m.getParameterCount()]), m.getName());
            assertTrue(e.getCause() instanceof GraphQLException, m.getName() + ": " + e.getCause());
        }

        assertTrue(publicos >= 14, "metodos publicos encontrados: " + publicos);
        // Tres recibos (finiquito y los dos de ítems) controlan por el alcance en vez de seg.requireVer().
        verify(seg, times(publicos - 3)).requireVer();
        verify(alcance).requireVerFiniquito(null);
        verify(alcance).requireVerItemDeLiquidacion(null);
        verify(alcance).requireVerItemDeFiniquito(null);
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

        verify(seg, times(2)).requireVer();
        verify(alcance).requireVerFiniquito(500L);
    }
}
