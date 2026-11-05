package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.VentaTarjetaInput;
import com.franco.dev.service.financiero.VentaTarjetaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static com.franco.dev.graphql.financiero.RolCajaSoporte.USUARIO_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Issue #574: un cobro con tarjeta lo lee o lo toca el dueño de la caja del cobro, quien concilia cupones (VENTA
 * TARJETA COMPLETAR) o la lectura de caja; ADMIN pasa siempre.
 */
class VentaTarjetaConsultasRolTest {

    private static final Long SUC = 3L;
    private static final Long COBRO_AJENO = 10L;
    private static final Long COBRO_PROPIO = 11L;
    private static final Long VENTA_AJENA = 20L;
    private static final Long VENTA_PROPIA = 21L;
    private static final Long VENTA_SIN_COBRO = 22L;

    private RolCajaSoporte rol;
    private VentaTarjetaGraphQL resolver;
    private VentaTarjetaService service;

    @BeforeEach
    void setUp() {
        rol = new RolCajaSoporte();
        resolver = rol.resolver(VentaTarjetaGraphQL.class);
        service = mock(VentaTarjetaService.class);
        ReflectionTestUtils.setField(resolver, "service", service);
        VentaTarjeta ajeno = cobro(COBRO_AJENO, 5L);
        VentaTarjeta propio = cobro(COBRO_PROPIO, USUARIO_ID);
        when(service.findByIdAndSucursalId(COBRO_AJENO, SUC)).thenReturn(ajeno);
        when(service.findByIdAndSucursalId(COBRO_PROPIO, SUC)).thenReturn(propio);
        when(service.findByVentaIdAndSucursalId(VENTA_AJENA, SUC)).thenReturn(ajeno);
        when(service.findByVentaIdAndSucursalId(VENTA_PROPIA, SUC)).thenReturn(propio);
        when(service.findAllByVentaIdAndSucursalId(VENTA_AJENA, SUC)).thenReturn(Collections.singletonList(ajeno));
        when(service.findAllByVentaIdAndSucursalId(VENTA_PROPIA, SUC)).thenReturn(Collections.singletonList(propio));
        when(service.findAllByVentaIdAndSucursalId(VENTA_SIN_COBRO, SUC)).thenReturn(Collections.emptyList());
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RolCajaSoporte.limpiar();
    }

    private static VentaTarjeta cobro(Long id, Long duenioDeLaCaja) {
        Usuario u = new Usuario();
        u.setId(duenioDeLaCaja);
        PdvCaja caja = new PdvCaja();
        caja.setId(100L + id);
        caja.setSucursalId(SUC);
        caja.setUsuario(u);
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(id);
        vt.setSucursalId(SUC);
        vt.setCaja(caja);
        return vt;
    }

    private static VentaTarjetaInput input(Long id, Long ventaId) {
        VentaTarjetaInput in = new VentaTarjetaInput();
        in.setId(id);
        in.setVentaId(ventaId);
        in.setSucursalId(SUC);
        return in;
    }

    private static void noAutorizado(Runnable r) {
        Throwable t = assertThrows(Throwable.class, r::run);
        assertTrue(RolCajaSoporte.esNoAutorizado(t), "esperaba No autorizado y vino: " + t);
    }

    @Test
    void elCajeroLeeSuCobroYNoElDeOtro() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        assertNotNull(resolver.ventaTarjetaPorId(COBRO_PROPIO, SUC));
        assertNotNull(resolver.ventaTarjetaPorVentaId(VENTA_PROPIA, SUC));
        noAutorizado(() -> resolver.ventaTarjetaPorId(COBRO_AJENO, SUC));
        noAutorizado(() -> resolver.ventaTarjetaPorVentaId(VENTA_AJENA, SUC));
    }

    @Test
    void conciliacionLecturaDeCajaYAdminLeenCobrosAjenos() {
        for (String r : new String[]{"VENTA TARJETA COMPLETAR", "ANALISIS DE CAJA", "TESORERIA VER",
                "TESORERIA GESTIONAR", "ADMIN"}) {
            rol.autenticar("U", r);
            assertNotNull(resolver.ventaTarjetaPorId(COBRO_AJENO, SUC), r);
            assertNotNull(resolver.ventaTarjetaPorVentaId(VENTA_AJENA, SUC), r);
        }
    }

    @Test
    void unCobroInexistenteRespondeComoHoy() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        assertNull(resolver.ventaTarjetaPorId(99L, SUC));
        assertNull(resolver.ventaTarjetaPorVentaId(99L, SUC));
    }

    @Test
    void elCajeroNoReescribeNiDuplicaElCobroDeOtro() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        noAutorizado(() -> resolver.updateVentaTarjeta(input(COBRO_AJENO, null)));
        noAutorizado(() -> resolver.saveVentaTarjeta(input(COBRO_AJENO, null)));
        noAutorizado(() -> resolver.saveVentaTarjeta(input(null, VENTA_AJENA)));
        verify(service, never()).save(any());
    }

    @Test
    void elCajeroCompletaSuCobro() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        assertNotNull(resolver.updateVentaTarjeta(input(COBRO_PROPIO, null)));
        verify(service).save(any());
    }

    @Test
    void conciliacionCompletaElCobroDeOtro() {
        rol.autenticar("U", "VENTA TARJETA COMPLETAR");
        assertNotNull(resolver.updateVentaTarjeta(input(COBRO_AJENO, null)));
    }

    @Test
    void unaVentaSinCobroSeRegistraComoHoy() {
        com.franco.dev.service.operaciones.VentaService ventas = mock(com.franco.dev.service.operaciones.VentaService.class);
        when(ventas.findByIdAndSucursalId(VENTA_SIN_COBRO, SUC)).thenReturn(new com.franco.dev.domain.operaciones.Venta());
        ReflectionTestUtils.setField(resolver, "ventaService", ventas);
        rol.autenticar("CAJERO", "VENTA TOUCH");
        assertNotNull(resolver.saveVentaTarjeta(input(null, VENTA_SIN_COBRO)));
        verify(service).save(any());
    }
}
