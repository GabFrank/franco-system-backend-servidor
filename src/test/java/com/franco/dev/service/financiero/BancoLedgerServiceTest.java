package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reversa de un movimiento bancario: una sola vez, con lock y con el estado leido de la base (issue #376). */
class BancoLedgerServiceTest {

    private CuentaBancariaRepository cuentaRepository;
    private MovimientoBancarioRepository movimientoRepository;
    private com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository configRepository;
    private BancoLedgerService service;
    private javax.persistence.EntityManager em;

    private CuentaBancaria cuenta;

    @BeforeEach
    void setUp() {
        cuentaRepository = mock(CuentaBancariaRepository.class);
        movimientoRepository = mock(MovimientoBancarioRepository.class);
        configRepository = mock(com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository.class);
        when(configRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        service = new BancoLedgerService(cuentaRepository, movimientoRepository, new LimiteAnulacionService(configRepository));
        em = mock(javax.persistence.EntityManager.class);
        service.setEntityManager(em);

        cuenta = new CuentaBancaria();
        cuenta.setId(4L);
        cuenta.setSaldo(new BigDecimal("1000"));
        when(cuentaRepository.lockById(4L)).thenReturn(Optional.of(cuenta));
        when(movimientoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private MovimientoBancario salida(long id, String monto) {
        MovimientoBancario m = new MovimientoBancario();
        m.setId(id);
        m.setCuentaBancaria(cuenta);
        m.setTipoMovimiento(MovimientoBancarioTipo.SALIDA_MANUAL);
        m.setMonto(new BigDecimal(monto));
        m.setAnulado(false);
        return m;
    }

    @Test
    void registrar_parte_del_saldo_de_la_base_y_no_del_que_tenia_la_instancia_ya_cargada() {
        // Otro movimiento commiteo mientras este esperaba el lock: la base dice 1500, la instancia sigue en 1000.
        doAnswer(i -> {
            ((CuentaBancaria) i.getArgument(0)).setSaldo(new BigDecimal("1500"));
            return null;
        }).when(em).refresh(cuenta);

        MovimientoBancario m = service.registrar(4L, MovimientoBancarioTipo.ENTRADA_MANUAL, new BigDecimal("200"),
                "DEPOSITO", "MANUAL", null, null);

        assertEquals(0, new BigDecimal("1500").compareTo(m.getSaldoAnterior()));
        assertEquals(0, new BigDecimal("1700").compareTo(cuenta.getSaldo()), "piso el movimiento del otro");
        org.mockito.InOrder orden = inOrder(cuentaRepository, em);
        orden.verify(cuentaRepository).lockById(4L);
        orden.verify(em).refresh(cuenta);
        orden.verify(cuentaRepository).save(cuenta);
    }

    @Test
    void la_reserva_de_un_cheque_diferido_tambien_se_calcula_sobre_lo_que_hay_en_la_base() {
        cuenta.setSaldoReservado(new BigDecimal("100"));
        doAnswer(i -> {
            ((CuentaBancaria) i.getArgument(0)).setSaldoReservado(new BigDecimal("400"));
            return null;
        }).when(em).refresh(cuenta);

        service.ajustarReservado(4L, new BigDecimal("50"));

        assertEquals(0, new BigDecimal("450").compareTo(cuenta.getSaldoReservado()));
    }

    @Test
    void revertir_una_salida_devuelve_el_monto_y_marca_el_original_anulado() {
        MovimientoBancario original = salida(20L, "300");

        MovimientoBancario contra = service.revertir(original, "ANULACION PAGO #1", null);

        assertEquals(MovimientoBancarioTipo.AJUSTE_POSITIVO, contra.getTipoMovimiento());
        assertEquals(0, new BigDecimal("1300").compareTo(cuenta.getSaldo()));
        assertEquals(Boolean.TRUE, original.getAnulado());
    }

    @Test
    void revertir_mira_el_estado_de_la_base_y_no_el_de_la_instancia_que_le_pasan() {
        // Otra transaccion ya lo revirtio y commiteo mientras esta esperaba el lock: la instancia que trae
        // el llamador sigue diciendo que no esta anulado. Con el chequeo viejo, sobre la instancia, pasaba.
        MovimientoBancario original = salida(21L, "300");
        when(movimientoRepository.findAnuladoById(21L)).thenReturn(Optional.of(true));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.revertir(original, "ANULACION PAGO #2", null));

        assertTrue(e.getMessage().contains("#21") && e.getMessage().contains("ya está anulado"), e.getMessage());
        verify(movimientoRepository, never()).save(any());
        assertEquals(0, new BigDecimal("1000").compareTo(cuenta.getSaldo()));
    }

    @Test
    void revertir_toma_el_lock_del_movimiento_antes_de_leer_el_estado_y_antes_que_la_cuenta() {
        MovimientoBancario original = salida(22L, "300");
        when(movimientoRepository.findAnuladoById(22L)).thenReturn(Optional.of(false));

        service.revertir(original, "ANULACION PAGO #3", null);

        InOrder orden = inOrder(movimientoRepository, cuentaRepository);
        orden.verify(movimientoRepository).lockById(22L);
        orden.verify(movimientoRepository).findAnuladoById(22L);
        orden.verify(cuentaRepository).lockById(4L);
    }

    @Test
    void revertir_dos_veces_el_mismo_movimiento_rechaza_la_segunda() {
        MovimientoBancario original = salida(23L, "300");

        service.revertir(original, "ANULACION PAGO #4", null);

        assertThrows(GraphQLException.class, () -> service.revertir(original, "ANULACION PAGO #4", null));
        assertEquals(0, new BigDecimal("1300").compareTo(cuenta.getSaldo()));
    }

    @Test
    void revertir_con_anulado_nulo_lo_trata_como_vigente() {
        MovimientoBancario original = salida(24L, "300");
        original.setAnulado(null);

        service.revertir(original, "ANULACION PAGO #5", null);

        assertEquals(Boolean.TRUE, original.getAnulado());
        assertEquals(0, new BigDecimal("1300").compareTo(cuenta.getSaldo()));
    }

    @Test
    void revertir_un_movimiento_sin_registrar_se_rechaza() {
        MovimientoBancario sinId = salida(0L, "300");
        sinId.setId(null);

        assertThrows(GraphQLException.class, () -> service.revertir(sinId, "x", null));
        verify(movimientoRepository, never()).save(any());
    }

    private void conLimiteDeDias(int dias) {
        com.franco.dev.domain.empresarial.ConfiguracionGeneral config = new com.franco.dev.domain.empresarial.ConfiguracionGeneral();
        config.setDiasLimiteAnulacion(dias);
        when(configRepository.findAll()).thenReturn(java.util.Collections.singletonList(config));
    }

    // Issue #370: el tope de antiguedad no se miraba en ninguna reversa bancaria.
    @Test
    void revertir_un_movimiento_mas_viejo_que_el_limite_se_rechaza_sin_postear_ni_marcarlo_anulado() {
        conLimiteDeDias(5);
        MovimientoBancario original = salida(25L, "300");
        original.setCreadoEn(java.time.LocalDateTime.now().minusDays(9));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.revertir(original, "ANULACION PAGO #6", null));

        assertTrue(e.getMessage().contains("El movimiento bancario #25")
                && e.getMessage().contains("límite de 5 días"), e.getMessage());
        verify(movimientoRepository, never()).save(any());
        assertEquals(Boolean.FALSE, original.getAnulado());
        assertEquals(0, new BigDecimal("1000").compareTo(cuenta.getSaldo()));
    }

    @Test
    void revertir_un_movimiento_dentro_del_limite_lo_revierte() {
        conLimiteDeDias(5);
        MovimientoBancario original = salida(26L, "300");
        original.setCreadoEn(java.time.LocalDateTime.now().minusDays(2));

        service.revertir(original, "ANULACION PAGO #7", null);

        assertEquals(0, new BigDecimal("1300").compareTo(cuenta.getSaldo()));
    }
}
