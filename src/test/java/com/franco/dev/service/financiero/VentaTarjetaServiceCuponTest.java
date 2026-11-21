package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionVentaTarjeta;
import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.repository.financiero.ConfiguracionVentaTarjetaRepository;
import com.franco.dev.repository.financiero.VentaTarjetaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El cupon de un cobro ya COMPLETADO en otra venta (#195): lo pregunta la reactivacion de una venta antes
 * de devolver su cobro a COMPLETADO. Las consultas en si las ejerce la prueba de runtime contra Postgres;
 * aca, que se usen bien.
 */
class VentaTarjetaServiceCuponTest {

    private VentaTarjetaRepository repository;
    private ConfiguracionVentaTarjetaRepository configuracion;
    private VentaTarjetaService service;

    @BeforeEach
    void setUp() {
        repository = mock(VentaTarjetaRepository.class);
        configuracion = mock(ConfiguracionVentaTarjetaRepository.class);
        when(configuracion.findAllByOrderByIdAsc()).thenReturn(Collections.emptyList());
        when(repository.countQrCompletadoEnOtra(anyLong(), anyString(), anyLong(), any())).thenReturn(0L);
        when(repository.completadosPorCodigo(anyLong(), anyString(), any(), any(), anyLong(), any()))
                .thenReturn(Collections.emptyList());
        service = new VentaTarjetaService(repository, configuracion);
    }

    private static VentaTarjeta cobro(String qr, String codigo, String monto) {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(10L);
        vt.setSucursalId(4L);
        vt.setQrCrudo(qr);
        vt.setCodigoAutorizacion(codigo);
        vt.setMontoEscaneado(monto != null ? new BigDecimal(monto) : null);
        TerminalPos t = new TerminalPos();
        t.setId(3L);
        vt.setTerminalPos(t);
        return vt;
    }

    @Test
    void qrCompletadoEnOtraVentaDaTrue_yLoBuscaRecortado() {
        when(repository.countQrCompletadoEnOtra(4L, "QR-1", 10L, VentaEstado.CANCELADA)).thenReturn(1L);
        assertTrue(service.cuponCompletadoEnOtra(cobro("  QR-1\n", null, null)));
    }

    @Test
    void qrLibreYSinCodigoDaFalse() {
        assertFalse(service.cuponCompletadoEnOtra(cobro("QR-1", null, null)));
    }

    @Test
    void qrVacioNoConsultaPorQr() {
        assertFalse(service.cuponCompletadoEnOtra(cobro("   ", null, null)));
        verify(repository, never()).countQrCompletadoEnOtra(anyLong(), anyString(), anyLong(), any());
    }

    @Test
    void mismoCodigoYMismoMontoDaTrue_conElCodigoEnMayusculasYLaTerminal() {
        when(repository.completadosPorCodigo(eq(4L), eq("AB12"), eq(3L), any(LocalDateTime.class), eq(10L),
                eq(VentaEstado.CANCELADA))).thenReturn(Collections.singletonList(cobro(null, "AB12", "50.00")));
        assertTrue(service.cuponCompletadoEnOtra(cobro(null, " ab12 ", "50")));
    }

    /** Montos distintos: es una colision de codigo corto, no el mismo cupon. */
    @Test
    void mismoCodigoConOtroMontoDaFalse() {
        when(repository.completadosPorCodigo(anyLong(), anyString(), any(), any(), anyLong(), any()))
                .thenReturn(Collections.singletonList(cobro(null, "AB12", "60")));
        assertFalse(service.cuponCompletadoEnOtra(cobro(null, "AB12", "50")));
    }

    /** Si falta un monto no alcanza para descartar: se prefiere avisar de mas, como el filial. */
    @Test
    void mismoCodigoSinMontoDaTrue() {
        when(repository.completadosPorCodigo(anyLong(), anyString(), any(), any(), anyLong(), any()))
                .thenReturn(Collections.singletonList(cobro(null, "AB12", null)));
        assertTrue(service.cuponCompletadoEnOtra(cobro(null, "AB12", "50")));
    }

    @Test
    void ventanaSinConfiguracionOConCeroEs24Horas() {
        assertEquals(24, service.horasVentanaDuplicado());
        ConfiguracionVentaTarjeta cero = new ConfiguracionVentaTarjeta();
        cero.setHorasVentanaDuplicado(0);
        when(configuracion.findAllByOrderByIdAsc()).thenReturn(Collections.singletonList(cero));
        assertEquals(24, service.horasVentanaDuplicado());
        cero.setHorasVentanaDuplicado(6);
        assertEquals(6, service.horasVentanaDuplicado());
    }
}
