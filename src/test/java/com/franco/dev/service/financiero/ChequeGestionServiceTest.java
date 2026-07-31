package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.enums.EstadoCheque;
import com.franco.dev.domain.financiero.enums.EstadoChequera;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.service.financiero.ChequeService;
import com.franco.dev.service.financiero.ChequeraService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cheques (F7): contado debita y queda cobrado; diferido reserva; cobro/anulación. */
class ChequeGestionServiceTest {

    private ChequeService chequeService;
    private ChequeraService chequeraService;
    private com.franco.dev.repository.financiero.ChequeRepository chequeRepository;
    private com.franco.dev.repository.financiero.ChequeraRepository chequeraRepository;
    private BancoLedgerService bancoLedgerService;
    private ChequeGestionService service;

    private Chequera chequera;
    private CuentaBancaria cuenta;

    @BeforeEach
    void setUp() {
        chequeService = mock(ChequeService.class);
        chequeraService = mock(ChequeraService.class);
        chequeRepository = mock(com.franco.dev.repository.financiero.ChequeRepository.class);
        chequeraRepository = mock(com.franco.dev.repository.financiero.ChequeraRepository.class);
        bancoLedgerService = mock(BancoLedgerService.class);
        service = new ChequeGestionService(chequeService, chequeraService, chequeRepository, chequeraRepository, bancoLedgerService);

        cuenta = new CuentaBancaria(); cuenta.setId(4L);
        chequera = new Chequera(); chequera.setId(1L); chequera.setSiguienteNumero(100L);
        chequera.setRangoHasta(105.0); chequera.setEstado(EstadoChequera.ACTIVA); chequera.setCuentaBancaria(cuenta);

        when(chequeraRepository.lockById(1L)).thenReturn(Optional.of(chequera));
        when(chequeraService.save(any())).thenAnswer(i -> i.getArgument(0));
        when(chequeService.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Cheque nuevo(boolean diferido) {
        Cheque c = new Cheque();
        c.setChequera(chequera); c.setTotal(500000.0); c.setDiferido(diferido);
        return c;
    }

    @Test
    void emitir_contado_debita_y_queda_cobrado() {
        Cheque c = service.emitir(nuevo(false), null);
        assertEquals(EstadoCheque.COBRADO, c.getEstado());
        assertNotNull(c.getFechaCobro());
        verify(bancoLedgerService).registrar(eq(4L), eq(MovimientoBancarioTipo.SALIDA_MANUAL), any(), any(), any(), any(), any());
        assertEquals(101L, chequera.getSiguienteNumero());
    }

    @Test
    void emitir_diferido_reserva_saldo() {
        Cheque c = service.emitir(nuevo(true), null);
        assertEquals(EstadoCheque.DIFERIDO, c.getEstado());
        verify(bancoLedgerService).ajustarReservado(eq(4L), eq(new BigDecimal("500000.0")));
        verify(bancoLedgerService, never()).registrar(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cobrar_diferido_debita_y_libera_reserva() {
        Cheque c = nuevo(true); c.setId(9L); c.setEstado(EstadoCheque.DIFERIDO); c.setCuentaBancaria(cuenta);
        when(chequeRepository.lockById(9L)).thenReturn(Optional.of(c));

        Cheque r = service.cobrar(9L, null);
        assertEquals(EstadoCheque.COBRADO, r.getEstado());
        verify(bancoLedgerService).registrar(eq(4L), eq(MovimientoBancarioTipo.SALIDA_MANUAL), any(), any(), any(), any(), any());
        verify(bancoLedgerService).ajustarReservado(eq(4L), eq(new BigDecimal("500000.0").negate()));
    }

    @Test
    void anular_cobrado_falla() {
        Cheque c = nuevo(false); c.setId(9L); c.setEstado(EstadoCheque.COBRADO);
        when(chequeRepository.lockById(9L)).thenReturn(Optional.of(c));
        assertThrows(GraphQLException.class, () -> service.anular(9L, "x", null));
    }

    @Test
    void emitir_ultimo_numero_agota_chequera() {
        chequera.setSiguienteNumero(105L);
        service.emitir(nuevo(false), null);
        assertEquals(EstadoChequera.AGOTADA, chequera.getEstado());
    }
}
