package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.DeliveryGraphQL;
import com.franco.dev.graphql.operaciones.VentaGraphQL;
import com.franco.dev.service.financiero.PdvCajaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static com.franco.dev.graphql.financiero.RolCajaSoporte.USUARIO_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Issue #562: el detalle de una caja lo ve su dueño, ANALISIS DE CAJA o tesorería; ADMIN pasa siempre. */
class CajaDetalleAccesoTest {

    private static final Long SUC = 3L;
    private static final Long CAJA_AJENA = 40L;
    private static final Long CAJA_PROPIA = 41L;
    private static final Long CAJA_SIN_REPLICAR = 42L;

    private RolCajaSoporte rol;
    private PdvCajaService cajas;

    @BeforeEach
    void setUp() {
        rol = new RolCajaSoporte();
        cajas = mock(PdvCajaService.class);
        when(cajas.findById(CAJA_AJENA, SUC)).thenReturn(caja(CAJA_AJENA, 5L));
        when(cajas.findById(CAJA_PROPIA, SUC)).thenReturn(caja(CAJA_PROPIA, USUARIO_ID));
    }

    @AfterEach
    void tearDown() {
        RolCajaSoporte.limpiar();
    }

    private static PdvCaja caja(Long id, Long duenio) {
        PdvCaja c = new PdvCaja();
        c.setId(id);
        c.setSucursalId(SUC);
        Usuario u = new Usuario();
        u.setId(duenio);
        c.setUsuario(u);
        return c;
    }

    private void exigir(Long cajaId, Long sucId) {
        PdvCajaGraphQL.exigirLecturaDeCaja(rol.seg, cajas, cajaId, sucId);
    }

    private static void noAutorizado(Runnable r) {
        Throwable t = assertThrows(Throwable.class, r::run);
        assertTrue(RolCajaSoporte.esNoAutorizado(t), "esperaba No autorizado y vino: " + t);
    }

    @Test
    void elCajeroLeeSuCajaYNoLaDeOtro() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        assertDoesNotThrow(() -> exigir(CAJA_PROPIA, SUC));
        noAutorizado(() -> exigir(CAJA_AJENA, SUC));
    }

    @Test
    void analisisTesoreriaYAdminLeenCajaAjena() {
        for (String r : new String[]{"ANALISIS DE CAJA", "TESORERIA VER", "TESORERIA GESTIONAR",
                "TESORERIA CPC COBRAR", "TESORERIA CPP PAGAR", "ADMIN"}) {
            rol.autenticar("U", r);
            assertDoesNotThrow(() -> exigir(CAJA_AJENA, SUC), r);
        }
    }

    @Test
    void tesoreriaCajaEInventarioNoLeenCajaAjena() {
        for (String r : new String[]{"TESORERIA CAJA", "VER INVENTARIO", "ANALISIS DE VENTA"}) {
            rol.autenticar("U", r);
            noAutorizado(() -> exigir(CAJA_AJENA, SUC));
        }
    }

    @Test
    void cajaQueElCentralTodaviaNoTieneRespondeComoHoy() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        assertDoesNotThrow(() -> exigir(CAJA_SIN_REPLICAR, SUC));
    }

    @Test
    void sinSucursalSoloElRol() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        noAutorizado(() -> exigir(CAJA_PROPIA, null));
        rol.autenticar("U", "ANALISIS DE CAJA");
        assertDoesNotThrow(() -> exigir(CAJA_PROPIA, null));
        verify(cajas, never()).findById(CAJA_PROPIA, null);
    }

    @Test
    void lasConsultasPorCajaRechazanAlCajeroAjeno() {
        VentaGraphQL ventas = rol.resolver(VentaGraphQL.class);
        GastoGraphQL gastos = rol.resolver(GastoGraphQL.class);
        VentaTarjetaGraphQL tarjetas = rol.resolver(VentaTarjetaGraphQL.class);
        DeliveryGraphQL deliverys = rol.resolver(DeliveryGraphQL.class);
        for (Object r : new Object[]{ventas, gastos, tarjetas, deliverys}) {
            ReflectionTestUtils.setField(r, "pdvCajaService", cajas);
        }
        rol.autenticar("CAJERO", "VENTA TOUCH");
        noAutorizado(() -> ventas.ventasPorCajaId(null, CAJA_AJENA, 0, 15, true, SUC, null, null, null, null, null, null));
        noAutorizado(() -> gastos.gastosPorCajaId(CAJA_AJENA, SUC));
        noAutorizado(() -> tarjetas.ventasTarjetaPorCaja(CAJA_AJENA, SUC));
        noAutorizado(() -> tarjetas.countVentasTarjetaSinRegistrar(CAJA_AJENA, SUC));
        noAutorizado(() -> deliverys.deliveryPorCajaIdAndEstados(CAJA_AJENA, Collections.emptyList(), SUC));
    }
}
