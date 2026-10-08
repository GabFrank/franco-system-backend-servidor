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
    private BancoLedgerService service;

    private CuentaBancaria cuenta;

    @BeforeEach
    void setUp() {
        cuentaRepository = mock(CuentaBancariaRepository.class);
        movimientoRepository = mock(MovimientoBancarioRepository.class);
        service = new BancoLedgerService(cuentaRepository, movimientoRepository);

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
}
