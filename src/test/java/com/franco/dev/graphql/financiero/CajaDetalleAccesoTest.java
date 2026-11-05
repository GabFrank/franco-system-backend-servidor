package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.resolver.CajaResolver;
import com.franco.dev.graphql.operaciones.DeliveryGraphQL;
import com.franco.dev.graphql.operaciones.VentaGraphQL;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.service.financiero.CajaObservacionService;
import com.franco.dev.service.financiero.PdvCajaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Collections;

import static com.franco.dev.graphql.financiero.RolCajaSoporte.USUARIO_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        CajaObservacionGraphQL observaciones = rol.resolver(CajaObservacionGraphQL.class);
        for (Object r : new Object[]{ventas, gastos, tarjetas, deliverys, observaciones}) {
            ReflectionTestUtils.setField(r, "pdvCajaService", cajas);
        }
        rol.autenticar("CAJERO", "VENTA TOUCH");
        noAutorizado(() -> ventas.ventasPorCajaId(null, CAJA_AJENA, 0, 15, true, SUC, null, null, null, null, null, null));
        noAutorizado(() -> gastos.gastosPorCajaId(CAJA_AJENA, SUC));
        noAutorizado(() -> tarjetas.ventasTarjetaPorCaja(CAJA_AJENA, SUC));
        noAutorizado(() -> tarjetas.countVentasTarjetaSinRegistrar(CAJA_AJENA, SUC));
        noAutorizado(() -> deliverys.deliveryPorCajaIdAndEstados(CAJA_AJENA, Collections.emptyList(), SUC));
        noAutorizado(() -> observaciones.findByPdvCajaIdAndSucursalId(CAJA_AJENA, SUC));
    }

    // --- campos de la caja (fase 3) ---

    private CajaResolver campos() {
        CajaResolver r = rol.resolver(CajaResolver.class);
        ReflectionTestUtils.setField(r, "pdvCajaService", cajas);
        ReflectionTestUtils.setField(r, "cajaObservacionService", mock(CajaObservacionService.class));
        return r;
    }

    private static PdvCaja conConteos(PdvCaja c) {
        c.setConteoApertura(new Conteo());
        c.setConteoCierre(new Conteo());
        return c;
    }

    @Test
    void losConteosDeUnaCajaAjenaVienenNull() {
        CajaResolver r = campos();
        when(cajas.getRepository()).thenReturn(mock(PdvCajaRepository.class));
        PdvCaja ajena = conConteos(caja(CAJA_AJENA, 5L));
        rol.autenticar("CAJERO", "VENTA TOUCH");
        assertNull(r.conteoApertura(ajena));
        assertNull(r.conteoCierre(ajena));
        assertTrue(r.cajaObservacionList(ajena).isEmpty());

        PdvCaja propia = conConteos(caja(CAJA_PROPIA, USUARIO_ID));
        assertNotNull(r.conteoApertura(propia));
        assertNotNull(r.conteoCierre(propia));

        rol.autenticar("U", "ANALISIS DE CAJA");
        assertNotNull(r.conteoApertura(ajena));
        assertNotNull(r.conteoCierre(ajena));
    }

    @Test
    void elCajeroVeSoloElCierreDeLaCajaAnteriorDeSuMaletin() {
        CajaResolver r = campos();
        PdvCajaRepository repo = mock(PdvCajaRepository.class);
        when(cajas.getRepository()).thenReturn(repo);
        PdvCaja anterior = conConteos(caja(CAJA_AJENA, 5L));
        PdvCaja abierta = caja(CAJA_PROPIA, USUARIO_ID);
        Maletin m = new Maletin();
        m.setId(9L);
        abierta.setMaletin(m);
        abierta.setFechaApertura(LocalDateTime.now().minusHours(1));
        rol.autenticar("CAJERO", "VENTA TOUCH");
        when(cajas.findByUsuarioIdAndAbierto(USUARIO_ID)).thenReturn(Collections.singletonList(abierta));
        when(repo.findPreviousCajaByMaletinAndFecha(eq(9L), any(), any()))
                .thenReturn(Collections.singletonList(anterior));

        assertNotNull(r.conteoCierre(anterior));
        assertNull(r.conteoApertura(anterior));
        assertNull(r.conteoCierre(conConteos(caja(43L, 5L))));
    }

    @Test
    void unaCajaAnidadaQueNoReplicoNoTiraError() {
        CajaResolver r = campos();
        when(cajas.getRepository()).thenReturn(mock(PdvCajaRepository.class));
        PdvCaja sinFila = new PdvCaja() {
            @Override public Usuario getUsuario() { throw new EntityNotFoundException("sin fila"); }
        };
        sinFila.setId(CAJA_SIN_REPLICAR);
        sinFila.setConteoApertura(new Conteo());
        rol.autenticar("CAJERO", "VENTA TOUCH");
        assertNull(r.conteoApertura(sinFila));
        assertNull(r.balance(sinFila));
        rol.autenticar("U", "ANALISIS DE CAJA");
        assertNotNull(r.conteoApertura(sinFila));
    }

    @Test
    void elUsuarioYLosRolesSeLeenUnaVezPorRequest() {
        rol.autenticar("U", "ANALISIS DE CAJA");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try {
            clearInvocations(rol.usuarioService, rol.roleService);
            for (int i = 0; i < 5; i++) {
                assertTrue(PdvCajaGraphQL.puedeLeer(rol.seg, caja(CAJA_AJENA, 5L)));
            }
            verify(rol.usuarioService, times(1)).findByNickname("U");
            verify(rol.roleService, times(1)).findByUsuarioId(USUARIO_ID);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
