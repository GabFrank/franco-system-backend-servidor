package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaBalance;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.resolver.CajaResolver;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.service.financiero.FilialCajaProxyService;
import com.franco.dev.service.financiero.PdvCajaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static com.franco.dev.graphql.financiero.RolCajaSoporte.USUARIO_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Issue #524: una caja la lee su dueño, ANALISIS DE CAJA o tesorería; ADMIN pasa siempre. */
class PdvCajaAccesoTest {

    private static final Long SUC = 3L;
    private static final Long CAJA_AJENA = 40L;
    private static final Long OTRO_USUARIO = 5L;

    private RolCajaSoporte rol;
    private PdvCajaGraphQL resolver;
    private PdvCajaService service;
    private PdvCajaRepository repository;
    private FilialCajaProxyService proxy;

    @BeforeEach
    void setUp() {
        rol = new RolCajaSoporte();
        resolver = rol.resolver(PdvCajaGraphQL.class);
        service = mock(PdvCajaService.class);
        repository = mock(PdvCajaRepository.class);
        proxy = mock(FilialCajaProxyService.class);
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "filialCajaProxyService", proxy);
        when(service.getRepository()).thenReturn(repository);
        when(service.findById(CAJA_AJENA, SUC)).thenReturn(caja(CAJA_AJENA, OTRO_USUARIO));
        when(service.getBalance(any())).thenReturn(new CajaBalance());
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

    private static void noAutorizado(Runnable r) {
        Throwable t = assertThrows(Throwable.class, r::run);
        assertTrue(RolCajaSoporte.esNoAutorizado(t), "esperaba No autorizado y vino: " + t);
    }

    @Test
    void cajeroNoLeeLaCajaNiElBalanceDeOtro() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        noAutorizado(() -> resolver.pdvCaja(CAJA_AJENA, SUC));
        noAutorizado(() -> resolver.balancePorCajaIdAndSucursalId(CAJA_AJENA, SUC));
        noAutorizado(() -> resolver.reaperturasPorCaja(CAJA_AJENA, SUC));
    }

    @Test
    void elDuenioLeeSuCaja() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        when(service.findById(CAJA_AJENA, SUC)).thenReturn(caja(CAJA_AJENA, USUARIO_ID));
        assertNotNull(resolver.pdvCaja(CAJA_AJENA, SUC));
        assertNotNull(resolver.balancePorCajaIdAndSucursalId(CAJA_AJENA, SUC));
    }

    @Test
    void analisisTesoreriaYAdminLeenCajaAjena() {
        for (String r : new String[]{"ANALISIS DE CAJA", "TESORERIA VER", "TESORERIA GESTIONAR", "ADMIN"}) {
            rol.autenticar("X", r);
            assertNotNull(resolver.pdvCaja(CAJA_AJENA, SUC), r);
            assertNotNull(resolver.balancePorCajaIdAndSucursalId(CAJA_AJENA, SUC), r);
        }
    }

    @Test
    void tesoreriaCajaAcotadoNoLeeCajaAjena() {
        rol.autenticar("GERENTE", "TESORERIA CAJA");
        noAutorizado(() -> resolver.pdvCaja(CAJA_AJENA, SUC));
    }

    /** Caja que todavía no replicó al central: la respuesta de hoy, sin error (el poll de apertura reintenta). */
    @Test
    void cajaInexistenteRespondeComoHoy() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        assertNull(resolver.pdvCaja(99L, SUC));
        assertNotNull(resolver.balancePorCajaIdAndSucursalId(99L, SUC));
    }

    @Test
    void elCajeroLeeLaCajaAnteriorDeSuMaletinYNingunaOtra() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        PdvCaja propia = caja(41L, USUARIO_ID);
        Maletin m = new Maletin();
        m.setId(9L);
        propia.setMaletin(m);
        propia.setFechaApertura(LocalDateTime.now());
        when(service.findByUsuarioIdAndAbierto(USUARIO_ID)).thenReturn(Collections.singletonList(propia));
        when(repository.findPreviousCajaByMaletinAndFecha(eq9(), any(), any()))
                .thenReturn(Collections.singletonList(caja(CAJA_AJENA, OTRO_USUARIO)));

        assertNotNull(resolver.pdvCaja(CAJA_AJENA, SUC));

        when(service.findById(42L, SUC)).thenReturn(caja(42L, OTRO_USUARIO));
        noAutorizado(() -> resolver.pdvCaja(42L, SUC));
    }

    private static Long eq9() {
        return org.mockito.ArgumentMatchers.eq(9L);
    }

    @Test
    void lasConsultasPorUsuarioSoloConElDeLaSesion() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        noAutorizado(() -> resolver.cajaAbiertoPorUsuarioId(OTRO_USUARIO));
        noAutorizado(() -> resolver.cajasPorUsuarioId(OTRO_USUARIO, 0, 10));
        noAutorizado(() -> resolver.cajaAbiertoPorUsuarioIdPorSucursal(OTRO_USUARIO, SUC));
        noAutorizado(() -> resolver.cajasAbiertasPorUsuarioDesdeFiliales(OTRO_USUARIO));
        verifyNoInteractions(proxy);   // rechaza antes de salir a las filiales

        resolver.cajaAbiertoPorUsuarioId(USUARIO_ID);
        resolver.cajasAbiertasPorUsuarioDesdeFiliales(USUARIO_ID);
        verify(proxy).cajasAbiertasPorUsuarioDesdeFiliales(USUARIO_ID);

        rol.autenticar("X", "ANALISIS DE CAJA");
        resolver.cajasPorUsuarioId(OTRO_USUARIO, 0, 10);
    }

    @Test
    void pdvCajaDesdeFilialControlaLaCajaDevuelta() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        when(proxy.pdvCajaDesdeFilial(CAJA_AJENA, SUC)).thenReturn(caja(CAJA_AJENA, OTRO_USUARIO));
        noAutorizado(() -> resolver.pdvCajaDesdeFilial(CAJA_AJENA, SUC));

        when(proxy.pdvCajaDesdeFilial(CAJA_AJENA, SUC)).thenReturn(caja(CAJA_AJENA, USUARIO_ID));
        assertNotNull(resolver.pdvCajaDesdeFilial(CAJA_AJENA, SUC));

        when(proxy.pdvCajaDesdeFilial(anyLong(), anyLong())).thenReturn(null);
        assertNull(resolver.pdvCajaDesdeFilial(CAJA_AJENA, SUC));
    }

    /** El poll de apertura del desktop mira la caja propia del cajero, que puede no haber replicado todavía. */
    @Test
    void analisisDiferenciasDeLaCajaPropiaSinRol() {
        rol.autenticar("CAJERO", "VENTA TOUCH");
        when(service.findById(41L, SUC)).thenReturn(caja(41L, USUARIO_ID));
        resolver.cajasAnalisisDiferencias(41L, null, null, null, null, null, null, null, SUC, null, null, 0, 1);
        resolver.cajasAnalisisDiferencias(99L, null, null, null, null, null, null, null, SUC, null, null, 0, 1);

        noAutorizado(() -> resolver.cajasAnalisisDiferencias(CAJA_AJENA, null, null, null, null, null, null, null, SUC, null, null, 0, 1));
        // con cajaAnteriorId el servicio ignora cajaId: rol
        noAutorizado(() -> resolver.cajasAnalisisDiferencias(41L, CAJA_AJENA, null, null, null, null, null, null, SUC, null, null, 0, 1));
        noAutorizado(() -> resolver.cajasAnalisisDiferencias(null, null, null, null, null, null, null, null, SUC, null, null, 0, 1));
    }

    @Test
    void verificarCajaExigeAnalisisYGuardaAlDeLaSesion() {
        PdvCaja ajena = caja(CAJA_AJENA, OTRO_USUARIO);
        when(service.findById(CAJA_AJENA, SUC)).thenReturn(ajena);

        rol.autenticar("TESORERO", "TESORERIA GESTIONAR");
        noAutorizado(() -> resolver.verificarCaja(CAJA_AJENA, SUC, OTRO_USUARIO, true));
        assertNull(ajena.getVerificado());

        Usuario sesion = rol.autenticar("ANALISTA", "ANALISIS DE CAJA");
        assertTrue(resolver.verificarCaja(CAJA_AJENA, SUC, OTRO_USUARIO, true));
        assertTrue(ajena.getVerificado());
        assertEquals(sesion.getId(), ajena.getVerificadoPor().getId());
    }

    @Test
    void elCampoBalanceEsNullParaElQueNoPuedeLeer() {
        CajaResolver campo = rol.resolver(CajaResolver.class);
        ReflectionTestUtils.setField(campo, "pdvCajaService", service);

        rol.autenticar("CAJERO", "VENTA TOUCH");
        assertNull(campo.balance(caja(CAJA_AJENA, OTRO_USUARIO)));
        assertNotNull(campo.balance(caja(CAJA_AJENA, USUARIO_ID)));

        rol.autenticar("X", "ANALISIS DE CAJA");
        assertNotNull(campo.balance(caja(CAJA_AJENA, OTRO_USUARIO)));
    }
}
