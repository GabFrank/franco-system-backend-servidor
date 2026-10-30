package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Vuelto de un egreso de la caja mayor: moneda, tope del pendiente y egresos que lo admiten. */
class VueltoEgresoServiceTest {

    private MovimientoCajaVirtualRepository movimientos;
    private MonedaRepository monedas;
    private CambioService cambio;
    private TesoreriaService tesoreria;
    private VueltoEgresoService service;

    private final Moneda gs = moneda(1L, "GUARANI");
    private final Moneda usd = moneda(3L, "DOLAR");
    private final Usuario usuario = new Usuario();
    private MovimientoCajaVirtual egreso;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        movimientos = mock(MovimientoCajaVirtualRepository.class);
        monedas = mock(MonedaRepository.class);
        cambio = mock(CambioService.class);
        tesoreria = mock(TesoreriaService.class);
        IdempotenciaService idempotencia = mock(IdempotenciaService.class);
        when(idempotencia.ejecutar(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(i -> ((Supplier<Object>) i.getArgument(4)).get());
        service = new VueltoEgresoService(movimientos, monedas, cambio, tesoreria, mock(TesoreriaSecurityService.class),
                idempotencia, mock(PagoSolicitudDetalleRepository.class), mock(SolicitudPagoRepository.class),
                mock(GastoRepository.class));

        usuario.setId(1L);
        CajaVirtual caja = new CajaVirtual();
        caja.setId(7L);
        Funcionario responsable = new Funcionario();
        responsable.setId(40L);
        egreso = new MovimientoCajaVirtual();
        egreso.setId(100L);
        egreso.setCajaVirtual(caja);
        egreso.setTipoMovimiento(CajaVirtualTipoMovimiento.EGRESO);
        egreso.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
        egreso.setCantidad(100000.0);
        egreso.setMoneda(gs);
        egreso.setDescripcion("PAGO PARA CARNE");
        egreso.setResponsable(responsable);

        when(movimientos.lockById(100L)).thenReturn(Optional.of(egreso));
        when(movimientos.findActivoById(100L)).thenReturn(Optional.of(true));
        when(movimientos.sumVueltoActivo(100L)).thenReturn(new BigDecimal("30000"));
        when(monedas.findById(1L)).thenReturn(Optional.of(gs));
        when(monedas.findById(3L)).thenReturn(Optional.of(usd));
        when(cambio.findLastValorEnGsByMonedaId(3L)).thenReturn(7000.0);
        when(tesoreria.registrar(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Moneda moneda(Long id, String denominacion) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        return m;
    }

    @Test
    void vuelto_en_la_moneda_del_egreso_queda_como_ingreso_vinculado() {
        service.registrarVuelto(100L, 1L, 20000.0, "sobro", usuario, null);

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreria).registrar(cap.capture());
        MovimientoCajaVirtual v = cap.getValue();
        assertEquals(CajaVirtualTipoMovimiento.INGRESO, v.getTipoMovimiento());
        assertEquals(OrigenMovimientoTipo.VUELTO, v.getOrigenTipo());
        assertEquals(100L, v.getVueltoDeId());
        assertEquals(0, new BigDecimal("20000").compareTo(v.getVueltoEquivalente()));
        assertEquals(40L, v.getResponsable().getId());
        assertEquals("VUELTO DE #100 - PAGO PARA CARNE - SOBRO", v.getDescripcion());
    }

    @Test
    void vuelto_en_otra_moneda_se_convierte_con_la_cotizacion() {
        // 10 US$ × 7.000 = 70.000 Gs, justo lo pendiente (100.000 − 30.000).
        service.registrarVuelto(100L, 3L, 10.0, null, usuario, null);

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreria).registrar(cap.capture());
        assertEquals(10.0, cap.getValue().getCantidad());
        assertEquals(usd, cap.getValue().getMoneda());
        assertEquals(0, new BigDecimal("70000").compareTo(cap.getValue().getVueltoEquivalente()));
    }

    @Test
    void no_pasa_lo_pendiente() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.registrarVuelto(100L, 1L, 70002.0, null, usuario, null));
        assertTrue(e.getMessage().contains("supera lo pendiente"));
        verify(tesoreria, never()).registrar(any());
    }

    @Test
    void sin_cotizacion_no_convierte() {
        when(cambio.findLastValorEnGsByMonedaId(3L)).thenReturn(null);
        assertThrows(GraphQLException.class, () -> service.registrarVuelto(100L, 3L, 1.0, null, usuario, null));
        verify(tesoreria, never()).registrar(any());
    }

    @Test
    void egreso_anulado_de_maletin_o_de_otro_tipo_no_admite_vuelto() {
        when(movimientos.findActivoById(100L)).thenReturn(Optional.of(false));
        assertThrows(GraphQLException.class, () -> service.registrarVuelto(100L, 1L, 1.0, null, usuario, null));

        when(movimientos.findActivoById(100L)).thenReturn(Optional.of(true));
        egreso.setOrigenTipo(OrigenMovimientoTipo.RRHH_VALE);
        assertThrows(GraphQLException.class, () -> service.registrarVuelto(100L, 1L, 1.0, null, usuario, null));

        egreso.setOrigenTipo(OrigenMovimientoTipo.MALETIN);
        assertFalse(VueltoEgresoService.admiteVuelto(egreso));
        assertThrows(GraphQLException.class, () -> service.registrarVuelto(100L, 1L, 1.0, null, usuario, null));

        egreso.setTipoMovimiento(CajaVirtualTipoMovimiento.PAGO_PROVEEDOR);
        egreso.setOrigenTipo(OrigenMovimientoTipo.GASTO);
        assertTrue(VueltoEgresoService.admiteVuelto(egreso));
        egreso.setTipoMovimiento(CajaVirtualTipoMovimiento.INGRESO);
        egreso.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
        assertFalse(VueltoEgresoService.admiteVuelto(egreso));
        verify(tesoreria, never()).registrar(any());
    }
}
