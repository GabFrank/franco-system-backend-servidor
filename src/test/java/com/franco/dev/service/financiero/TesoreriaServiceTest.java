package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests del núcleo de tesorería (F1): saldo firmado por (caja, moneda), control de
 * descubierto (CN2), AJUSTE con signo, anulación con contra-movimiento y bloqueo
 * de anulación cross-módulo. Repositorios mockeados (lógica pura de dinero).
 */
class TesoreriaServiceTest {

    private CajaVirtualSaldoRepository saldoRepository;
    private CajaVirtualRepository cajaVirtualRepository;
    private MonedaRepository monedaRepository;
    private MovimientoCajaVirtualRepository movimientoRepository;
    private com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository configRepository;
    private TesoreriaService service;

    private CajaVirtual caja;
    private Moneda gs;
    private CajaVirtualSaldo saldo;

    @BeforeEach
    void setUp() {
        saldoRepository = mock(CajaVirtualSaldoRepository.class);
        cajaVirtualRepository = mock(CajaVirtualRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        movimientoRepository = mock(MovimientoCajaVirtualRepository.class);
        configRepository = mock(com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository.class);
        when(configRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        service = new TesoreriaService(saldoRepository, cajaVirtualRepository, monedaRepository, movimientoRepository, configRepository);

        caja = new CajaVirtual();
        caja.setId(1L);
        caja.setPermiteSaldoNegativo(false);

        gs = new Moneda();
        gs.setId(10L);
        gs.setDenominacion("GUARANIES");

        saldo = new CajaVirtualSaldo();
        saldo.setCajaVirtual(caja);
        saldo.setMoneda(gs);
        saldo.setSaldo(new BigDecimal("1000"));

        when(cajaVirtualRepository.findById(1L)).thenReturn(Optional.of(caja));
        when(saldoRepository.lockByCajaVirtualIdAndMonedaId(1L, 10L)).thenReturn(Optional.of(saldo));
        when(saldoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cajaVirtualRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(movimientoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private MovimientoCajaVirtual mov(CajaVirtualTipoMovimiento tipo, double cantidad) {
        MovimientoCajaVirtual m = new MovimientoCajaVirtual();
        m.setCajaVirtual(caja);
        m.setMoneda(gs);
        m.setTipoMovimiento(tipo);
        m.setCantidad(cantidad);
        return m;
    }

    @Test
    void ingreso_sube_saldo() {
        MovimientoCajaVirtual r = service.registrar(mov(CajaVirtualTipoMovimiento.INGRESO, 500));
        assertEquals(1000.0, r.getSaldoAnterior());
        assertEquals(1500.0, r.getSaldoPosterior());
        assertEquals(0, saldo.getSaldo().compareTo(new BigDecimal("1500")));
        // shim sincronizado
        assertEquals(1500.0, caja.getSaldoGs());
    }

    @Test
    void egreso_con_saldo_baja() {
        MovimientoCajaVirtual r = service.registrar(mov(CajaVirtualTipoMovimiento.EGRESO, 300));
        assertEquals(700.0, r.getSaldoPosterior());
        assertEquals(0, saldo.getSaldo().compareTo(new BigDecimal("700")));
    }

    @Test
    void egreso_sin_saldo_y_sin_permiso_falla() {
        assertThrows(GraphQLException.class,
                () -> service.registrar(mov(CajaVirtualTipoMovimiento.EGRESO, 2000)));
    }

    @Test
    void egreso_sin_saldo_con_permiso_negativo_ok() {
        caja.setPermiteSaldoNegativo(true);
        MovimientoCajaVirtual r = service.registrar(mov(CajaVirtualTipoMovimiento.EGRESO, 2000));
        assertEquals(-1000.0, r.getSaldoPosterior());
    }

    @Test
    void ajuste_negativo_resta() {
        MovimientoCajaVirtual r = service.registrar(mov(CajaVirtualTipoMovimiento.AJUSTE, -400));
        assertEquals(600.0, r.getSaldoPosterior());
    }

    @Test
    void ajuste_positivo_suma() {
        MovimientoCajaVirtual r = service.registrar(mov(CajaVirtualTipoMovimiento.AJUSTE, 400));
        assertEquals(1400.0, r.getSaldoPosterior());
    }

    @Test
    void anular_movimiento_manual_genera_contra_que_revierte() {
        MovimientoCajaVirtual original = mov(CajaVirtualTipoMovimiento.EGRESO, 300);
        original.setId(99L);
        original.setSaldoAnterior(1000.0);
        original.setSaldoPosterior(700.0); // efecto = -300
        original.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
        when(movimientoRepository.findById(99L)).thenReturn(Optional.of(original));

        service.anular(99L, "error de carga", null);

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        // Ahora revertir guarda 2 veces: el contra-movimiento y el original marcado inactivo.
        verify(movimientoRepository, times(2)).save(cap.capture());
        MovimientoCajaVirtual contra = cap.getAllValues().get(0);
        assertEquals(CajaVirtualTipoMovimiento.AJUSTE, contra.getTipoMovimiento());
        assertEquals(OrigenMovimientoTipo.ANULACION, contra.getOrigenTipo());
        assertEquals(99L, contra.getReferenciaId());
        assertEquals(300.0, contra.getCantidad()); // revierte el egreso: +300 (AJUSTE firmado)
        // El original queda inactivo (consistente con banco; la UI lo tacha).
        assertEquals(Boolean.FALSE, cap.getAllValues().get(1).getActivo());
    }

    @Test
    void anular_movimiento_de_otro_modulo_esta_bloqueado() {
        MovimientoCajaVirtual rrhh = mov(CajaVirtualTipoMovimiento.EGRESO, 300);
        rrhh.setId(50L);
        rrhh.setOrigenTipo(OrigenMovimientoTipo.RRHH_VALE);
        when(movimientoRepository.findById(50L)).thenReturn(Optional.of(rrhh));

        assertThrows(GraphQLException.class, () -> service.anular(50L, "x", null));
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void anular_un_contra_movimiento_esta_bloqueado() {
        MovimientoCajaVirtual contra = mov(CajaVirtualTipoMovimiento.AJUSTE, 300);
        contra.setId(77L);
        contra.setOrigenTipo(OrigenMovimientoTipo.ANULACION);
        when(movimientoRepository.findById(77L)).thenReturn(Optional.of(contra));

        assertThrows(GraphQLException.class, () -> service.anular(77L, "x", null));
    }

    @Test
    void moneda_null_cae_a_guarani() {
        when(monedaRepository.findFirstByDenominacionContainingIgnoreCaseOrderByIdAsc(any()))
                .thenReturn(gs);
        MovimientoCajaVirtual m = mov(CajaVirtualTipoMovimiento.INGRESO, 100);
        m.setMoneda(null);
        MovimientoCajaVirtual r = service.registrar(m);
        assertEquals(gs, r.getMoneda());
        assertEquals(1100.0, r.getSaldoPosterior());
    }
}
