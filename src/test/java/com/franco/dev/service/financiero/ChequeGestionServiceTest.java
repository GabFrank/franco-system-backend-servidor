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
    private IdempotenciaService idempotenciaService;
    private ChequeGestionService service;

    private Chequera chequera;
    private CuentaBancaria cuenta;

    @BeforeEach
    void setUp() {
        chequeService = mock(ChequeService.class);
        chequeraService = mock(ChequeraService.class);
        chequeRepository = mock(com.franco.dev.repository.financiero.ChequeRepository.class);
        chequeraRepository = mock(com.franco.dev.repository.financiero.ChequeraRepository.class);
        com.franco.dev.repository.financiero.MovimientoBancarioRepository movBancarioRepo =
                mock(com.franco.dev.repository.financiero.MovimientoBancarioRepository.class);
        bancoLedgerService = mock(BancoLedgerService.class);
        // Por defecto la clave es nueva (o no hay): la idempotencia deja correr la operacion.
        idempotenciaService = mock(IdempotenciaService.class);
        when(idempotenciaService.ejecutar(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> inv.<java.util.function.Supplier<Object>>getArgument(4).get());
        service = new ChequeGestionService(chequeService, chequeraService, chequeRepository, chequeraRepository,
                movBancarioRepo, bancoLedgerService, idempotenciaService);

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

    // ── Idempotencia de la emision (issue #376) ──

    /** La clave ya esta registrada con el cheque #77: la idempotencia no corre la accion, carga lo ya emitido. */
    private void claveYaUsadaPorElCheque(EstadoCheque estado) {
        Cheque existente = new Cheque();
        existente.setId(77L);
        existente.setEstado(estado);
        when(chequeRepository.findById(77L)).thenReturn(Optional.of(existente));
        // doAnswer y no when(...): al re-stubear, when invocaria la respuesta por defecto con argumentos nulos.
        doAnswer(inv -> inv.<java.util.function.Function<Long, Object>>getArgument(6).apply(77L))
                .when(idempotenciaService).ejecutar(eq("clave-1"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void emitir_repetido_con_la_misma_clave_devuelve_el_cheque_original_sin_emitir_otro() {
        claveYaUsadaPorElCheque(EstadoCheque.COBRADO);

        Cheque c = service.emitir(nuevo(false), null, "clave-1");

        assertEquals(77L, c.getId());
        assertEquals(100L, chequera.getSiguienteNumero());   // el correlativo no avanza
        verify(chequeraRepository, never()).lockById(anyLong());
        verify(bancoLedgerService, never()).registrar(anyLong(), any(), any(), any(), any(), any(), any());
        verify(bancoLedgerService, never()).ajustarReservado(anyLong(), any());
        verify(chequeService, never()).save(any());
    }

    @Test
    void emitir_repetido_de_un_cheque_ya_anulado_se_rechaza_y_no_emite_otro() {
        claveYaUsadaPorElCheque(EstadoCheque.ANULADO);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.emitir(nuevo(true), null, "clave-1"));

        assertTrue(e.getMessage().contains("anulado"), e.getMessage());
        assertEquals(100L, chequera.getSiguienteNumero());
        verify(bancoLedgerService, never()).ajustarReservado(anyLong(), any());
    }

    @Test
    void emitir_con_clave_nueva_emite_bajo_la_operacion_de_emision() {
        Cheque c = service.emitir(nuevo(true), null, "clave-2");

        assertEquals(EstadoCheque.DIFERIDO, c.getEstado());
        assertEquals(101L, chequera.getSiguienteNumero());
        verify(idempotenciaService).ejecutar(eq("clave-2"), eq(ChequeGestionService.OPERACION_EMITIR_CHEQUE),
                eq(ChequeGestionService.huellaDe(nuevo(true))), isNull(), any(), any(), any());
    }

    @Test
    void la_huella_de_emision_distingue_monto_y_tipo_y_no_la_hora_de_la_fecha_de_pago() {
        Cheque a = nuevo(true); a.setFechaPago(java.time.LocalDateTime.of(2026, 11, 8, 0, 0));
        Cheque b = nuevo(true); b.setFechaPago(java.time.LocalDateTime.of(2026, 11, 8, 15, 45));
        assertEquals(ChequeGestionService.huellaDe(a), ChequeGestionService.huellaDe(b));

        Cheque otroMonto = nuevo(true); otroMonto.setTotal(500001.0);
        assertNotEquals(ChequeGestionService.huellaDe(nuevo(true)), ChequeGestionService.huellaDe(otroMonto));
        assertNotEquals(ChequeGestionService.huellaDe(nuevo(true)), ChequeGestionService.huellaDe(nuevo(false)));
    }
}
