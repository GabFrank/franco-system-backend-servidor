package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.RetiroCasoRepository;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El contado de una verificación de retiro: ni negativo ni con más decimales de los que admite la
 * moneda. El ruido de punto flotante de un cliente que suma doubles se normaliza en vez de rechazarse.
 */
class RetiroVerificacionConteoValidoTest {

    private RetiroVerificacionRepository verificacionRepository;
    private RetiroCasoRepository casoRepository;
    private TesoreriaService tesoreria;
    private MonedaService monedaService;
    private RetiroVerificacionService service;

    private static Moneda moneda(long id, String denominacion, Integer decimales) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setDecimales(decimales);
        return m;
    }

    private static final Moneda GS = moneda(1, "GUARANI", 0);
    private static final Moneda RS = moneda(2, "REAL", 2);

    @BeforeEach
    void setUp() {
        RetiroRepository retiroRepository = mock(RetiroRepository.class);
        RetiroDetalleService retiroDetalleService = mock(RetiroDetalleService.class);
        verificacionRepository = mock(RetiroVerificacionRepository.class);
        casoRepository = mock(RetiroCasoRepository.class);
        CajaVirtualService cajaVirtualService = mock(CajaVirtualService.class);
        tesoreria = mock(TesoreriaService.class);
        monedaService = mock(MonedaService.class);
        service = new RetiroVerificacionService(retiroRepository, retiroDetalleService, verificacionRepository,
                casoRepository, mock(MovimientoCajaVirtualRepository.class), cajaVirtualService, monedaService,
                tesoreria, mock(TesoreriaSecurityService.class),
                mock(com.franco.dev.service.empresarial.SucursalService.class));

        Retiro retiro = new Retiro();
        retiro.setId(1151L);
        retiro.setSucursalId(24L);
        when(retiroRepository.lockByIdAndSucursalId(1151L, 24L)).thenReturn(Optional.of(retiro));
        when(verificacionRepository.findVigente(1151L, 24L)).thenReturn(Optional.empty());
        when(retiroRepository.findSituacion(1151L, 24L)).thenReturn(Optional.empty());
        when(cajaVirtualService.findById(3L)).thenReturn(Optional.of(new CajaVirtual()));
        when(monedaService.findById(1L)).thenReturn(Optional.of(GS));
        when(monedaService.findById(2L)).thenReturn(Optional.of(RS));

        RetiroDetalle gs = new RetiroDetalle();
        gs.setMoneda(GS);
        gs.setCantidad(1000000.0);
        RetiroDetalle rs = new RetiroDetalle();
        rs.setMoneda(RS);
        rs.setCantidad(100.0);
        when(retiroDetalleService.findByRetiroId(1151L, 24L)).thenReturn(Arrays.asList(gs, rs));
    }

    private static RetiroVerificacionService.ConteoMoneda conteo(long monedaId, String contado) {
        RetiroVerificacionService.ConteoMoneda c = new RetiroVerificacionService.ConteoMoneda();
        c.setMonedaId(monedaId);
        c.setContado(new BigDecimal(contado));
        return c;
    }

    private GraphQLException rechazo(RetiroVerificacionService.ConteoMoneda... conteos) {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.verificar(1151L, 24L, 3L, Arrays.asList(conteos), false, null, null));
        verify(verificacionRepository, never()).save(any());
        verifyNoInteractions(tesoreria, casoRepository);
        return e;
    }

    @Test
    void un_contado_negativo_se_rechaza_sin_guardar_nada() {
        GraphQLException e = rechazo(conteo(1, "1000000"), conteo(2, "-5"));
        assertTrue(e.getMessage().contains("REAL") && e.getMessage().contains("negativo"), e.getMessage());
    }

    @Test
    void guaranies_con_decimales_se_rechazan() {
        GraphQLException e = rechazo(conteo(1, "1000000.5"), conteo(2, "100"));
        assertTrue(e.getMessage().contains("admite hasta 0 decimales"), e.getMessage());
    }

    @Test
    void reales_con_tres_decimales_se_rechazan() {
        GraphQLException e = rechazo(conteo(1, "1000000"), conteo(2, "10.005"));
        assertTrue(e.getMessage().contains("admite hasta 2 decimales"), e.getMessage());
    }

    @Test
    void una_moneda_inexistente_se_rechaza_con_mensaje_claro() {
        GraphQLException e = rechazo(conteo(1, "1000000"), conteo(2, "100"), conteo(99, "5"));
        assertEquals("La moneda 99 no existe.", e.getMessage());
    }

    @Test
    void montos_validos_pasan_normalizados_a_los_decimales_de_la_moneda() {
        assertEquals(new BigDecimal("10.50"), RetiroVerificacionService.contadoValido(new BigDecimal("10.5"), RS, 1L));
        assertEquals(new BigDecimal("1000"), RetiroVerificacionService.contadoValido(new BigDecimal("1000.000"), GS, 1L));
        // Un Float grande llega en notación científica.
        assertEquals(new BigDecimal("10000000"), RetiroVerificacionService.contadoValido(new BigDecimal("1.0E7"), GS, 1L));
        assertEquals(new BigDecimal("0"), RetiroVerificacionService.contadoValido(BigDecimal.ZERO, GS, 1L));
    }

    @Test
    void el_ruido_de_punto_flotante_del_cliente_se_normaliza() {
        // Lo que arma mapearConteos con el Double 10.1 + 20.2 que suma un desktop anterior.
        BigDecimal ruidoso = new BigDecimal(Double.valueOf(10.1 + 20.2).toString());
        assertEquals(new BigDecimal("30.30"), RetiroVerificacionService.contadoValido(ruidoso, RS, 1L));
    }

    @Test
    void sin_decimales_cargados_usa_el_respaldo_de_la_moneda() {
        Moneda gsSinDato = moneda(1, "GUARANI", null);
        Moneda usSinDato = moneda(3, "DOLAR", null);
        assertEquals(0, RetiroVerificacionService.decimalesDe(gsSinDato));
        assertEquals(2, RetiroVerificacionService.decimalesDe(usSinDato));
        assertThrows(GraphQLException.class,
                () -> RetiroVerificacionService.contadoValido(new BigDecimal("500.5"), gsSinDato, 1L));
    }

    @Test
    void sin_conteo_de_una_moneda_declarada_no_es_negativo_ni_decimal() {
        // El contado ausente se toma como 0 (comportamiento de siempre): no lo rechaza esta validación.
        assertEquals(new BigDecimal("0.00"),
                RetiroVerificacionService.contadoValido(BigDecimal.ZERO, RS, 1L));
    }
}
