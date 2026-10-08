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
    private TesoreriaSecurityService seguridad;
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
        // El ACL de cajas se aplica en registrar/transferir/anular. Estos tests ejercitan la
        // aritmetica de saldos, no los permisos: el mock deja pasar todo (requireEscrituraCaja
        // es void y no hace nada por default en un mock).
        seguridad = mock(TesoreriaSecurityService.class);
        service = new TesoreriaService(saldoRepository, seguridad, cajaVirtualRepository, monedaRepository, movimientoRepository, configRepository);

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
        when(movimientoRepository.lockById(99L)).thenReturn(Optional.of(original));

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
        when(movimientoRepository.lockById(50L)).thenReturn(Optional.of(rrhh));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(50L, "x", null));
        assertTrue(e.getMessage().contains("proviene de RRHH_VALE"), e.getMessage());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void anular_un_contra_movimiento_esta_bloqueado() {
        MovimientoCajaVirtual contra = mov(CajaVirtualTipoMovimiento.AJUSTE, 300);
        contra.setId(77L);
        contra.setOrigenTipo(OrigenMovimientoTipo.ANULACION);
        when(movimientoRepository.lockById(77L)).thenReturn(Optional.of(contra));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(77L, "x", null));
        assertTrue(e.getMessage().contains("contra-movimiento"), e.getMessage());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void anular_dos_veces_el_mismo_movimiento_rechaza_la_segunda() {
        MovimientoCajaVirtual original = mov(CajaVirtualTipoMovimiento.EGRESO, 300);
        original.setId(99L);
        original.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
        when(movimientoRepository.lockById(99L)).thenReturn(Optional.of(original));

        service.anular(99L, "error de carga", null);
        assertEquals(0, new BigDecimal("1300").compareTo(saldo.getSaldo())); // el egreso de 300 quedó revertido

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(99L, "otra vez", null));
        assertTrue(e.getMessage().contains("ya está anulado"), e.getMessage());
        // Un solo contra-movimiento: los 2 save son el contra y el original inactivo, de la primera.
        verify(movimientoRepository, times(2)).save(any());
        assertEquals(0, new BigDecimal("1300").compareTo(saldo.getSaldo()));
    }

    @Test
    void anular_sin_permiso_sobre_la_caja_no_revela_que_ya_estaba_anulado() {
        MovimientoCajaVirtual anulado = mov(CajaVirtualTipoMovimiento.INGRESO, 200);
        anulado.setId(97L);
        anulado.setActivo(false);
        anulado.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
        when(movimientoRepository.lockById(97L)).thenReturn(Optional.of(anulado));
        doThrow(new GraphQLException("Sin permiso sobre la caja")).when(seguridad).requireEscrituraCaja(1L);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(97L, "x", null));
        assertTrue(e.getMessage().contains("Sin permiso"), e.getMessage());
    }

    @Test
    void anular_con_activo_nulo_lo_trata_como_activo() {
        MovimientoCajaVirtual original = mov(CajaVirtualTipoMovimiento.INGRESO, 200);
        original.setId(98L);
        original.setActivo(null);
        original.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
        when(movimientoRepository.lockById(98L)).thenReturn(Optional.of(original));

        service.anular(98L, "x", null);

        assertEquals(Boolean.FALSE, original.getActivo());
        assertEquals(0, new BigDecimal("800").compareTo(saldo.getSaldo()));
    }

    @Test
    void anular_un_movimiento_inexistente_lo_dice() {
        when(movimientoRepository.lockById(404L)).thenReturn(Optional.empty());

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(404L, "x", null));
        assertTrue(e.getMessage().contains("no encontrado"), e.getMessage());
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

    // ── revertir: un movimiento se revierte una sola vez (issue #376) ──

    private MovimientoCajaVirtual egresoDeVale(long id) {
        MovimientoCajaVirtual m = mov(CajaVirtualTipoMovimiento.EGRESO, 300);
        m.setId(id);
        m.setOrigenTipo(OrigenMovimientoTipo.RRHH_VALE);
        return m;
    }

    @Test
    void revertir_dos_veces_el_mismo_movimiento_rechaza_la_segunda_y_no_devuelve_la_plata_otra_vez() {
        MovimientoCajaVirtual original = egresoDeVale(60L);

        service.revertir(original, "ANULACION VALE #1", null);
        assertEquals(0, new BigDecimal("1300").compareTo(saldo.getSaldo()));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.revertir(original, "ANULACION VALE #1", null));
        assertTrue(e.getMessage().contains("#60") && e.getMessage().contains("ya está anulado"), e.getMessage());
        verify(movimientoRepository, times(2)).save(any());   // el contra y el original inactivo, de la primera
        assertEquals(0, new BigDecimal("1300").compareTo(saldo.getSaldo()));
    }

    @Test
    void revertir_mira_el_estado_de_la_base_y_no_el_de_la_instancia_que_le_pasan() {
        // Otra transaccion ya lo revirtio y commiteo mientras esta esperaba el lock: la instancia que trae
        // el llamador sigue diciendo activo.
        MovimientoCajaVirtual original = egresoDeVale(61L);
        when(movimientoRepository.findActivoById(61L)).thenReturn(Optional.of(false));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.revertir(original, "ANULACION VALE #2", null));

        assertTrue(e.getMessage().contains("ya está anulado"), e.getMessage());
        verify(movimientoRepository, never()).save(any());
        assertEquals(0, new BigDecimal("1000").compareTo(saldo.getSaldo()));
    }

    @Test
    void revertir_toma_el_lock_antes_de_leer_el_estado() {
        MovimientoCajaVirtual original = egresoDeVale(62L);
        when(movimientoRepository.findActivoById(62L)).thenReturn(Optional.of(true));

        service.revertir(original, "ANULACION VALE #3", null);

        org.mockito.InOrder orden = inOrder(movimientoRepository, saldoRepository);
        orden.verify(movimientoRepository).lockById(62L);
        orden.verify(movimientoRepository).findActivoById(62L);
        orden.verify(saldoRepository).lockByCajaVirtualIdAndMonedaId(1L, 10L);
    }

    @Test
    void revertir_con_activo_nulo_lo_trata_como_activo() {
        MovimientoCajaVirtual original = egresoDeVale(63L);
        original.setActivo(null);

        service.revertir(original, "ANULACION VALE #4", null);

        assertEquals(Boolean.FALSE, original.getActivo());
        assertEquals(0, new BigDecimal("1300").compareTo(saldo.getSaldo()));
    }

    @Test
    void revertir_un_movimiento_sin_registrar_se_rechaza() {
        MovimientoCajaVirtual sinId = mov(CajaVirtualTipoMovimiento.EGRESO, 300);

        assertThrows(GraphQLException.class, () -> service.revertir(sinId, "x", null));
        verify(movimientoRepository, never()).save(any());
    }
}
