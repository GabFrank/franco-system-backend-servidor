package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.financiero.FilialCajaProxyService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** reabrirCajaDesdeServidor (issue desktop #662): solo ADMIN, con motivo, la escritura la hace la filial. */
class PdvCajaGraphQLReabrirCajaTest {

    private static final Long CAJA = 50L;
    private static final Long SUCURSAL = 3L;
    private static final Long USUARIO = 7L;

    @Mock private PdvCajaService service;
    @Mock private UsuarioService usuarioService;
    @Mock private FilialCajaProxyService filialCajaProxyService;
    @Mock private TesoreriaSecurityService seg;

    @InjectMocks private PdvCajaGraphQL resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(usuarioService.tieneRol(USUARIO, "ADMIN")).thenReturn(true);
        Usuario sesion = new Usuario();
        sesion.setId(USUARIO);
        when(seg.requireUsuarioDeSesion()).thenReturn(sesion);
        when(service.findById(CAJA, SUCURSAL)).thenReturn(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void reenviaALaFilialYDevuelveLaCaja() throws Exception {
        when(filialCajaProxyService.reabrirCajaEnFilial(CAJA, SUCURSAL, "CERRO SIN QUERER")).thenReturn(CAJA);
        CajaFilialOperacionResult r = resolver.reabrirCajaDesdeServidor(CAJA, SUCURSAL, "  CERRO SIN QUERER ");
        assertTrue(r.getExito());
        assertEquals(CAJA, r.getCajaId());
    }

    @Test
    void sinAdminNoSaleALaRed() {
        when(usuarioService.tieneRol(USUARIO, "ADMIN")).thenReturn(false);
        assertThrows(GraphQLException.class, () -> resolver.reabrirCajaDesdeServidor(CAJA, SUCURSAL, "x"));
        verifyNoInteractions(filialCajaProxyService);
    }

    @Test
    void sinMotivoNoSaleALaRed() {
        assertThrows(GraphQLException.class, () -> resolver.reabrirCajaDesdeServidor(CAJA, SUCURSAL, "  "));
        verifyNoInteractions(filialCajaProxyService);
    }

    @Test
    void cajaVerificadaEnLaCopiaNoSaleALaRed() {
        PdvCaja caja = new PdvCaja();
        caja.setVerificado(true);
        when(service.findById(CAJA, SUCURSAL)).thenReturn(caja);
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.reabrirCajaDesdeServidor(CAJA, SUCURSAL, "x"));
        assertTrue(e.getMessage().contains("verificada"));
        verifyNoInteractions(filialCajaProxyService);
    }

    @Test
    void elRechazoDeLaFilialLlegaTalCual() throws Exception {
        when(filialCajaProxyService.reabrirCajaEnFilial(any(), any(), any()))
                .thenThrow(new Exception("El cajero ya tiene otra caja abierta"));
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.reabrirCajaDesdeServidor(CAJA, SUCURSAL, "x"));
        assertEquals("El cajero ya tiene otra caja abierta", e.getMessage());
    }

    @Test
    void timeoutNoAfirmaQueNoSeReabrio() throws Exception {
        when(filialCajaProxyService.reabrirCajaEnFilial(any(), any(), any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Read timed out"));
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.reabrirCajaDesdeServidor(CAJA, SUCURSAL, "x"));
        assertTrue(e.getMessage().contains("No se pudo confirmar"));
    }
}
