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
        // El refresh trae lo que hay en la base; por defecto, lo mismo que la instancia cargada.
        entityManager = mock(javax.persistence.EntityManager.class);
        doAnswer(i -> { if (enLaBase != null) enLaBase.accept(i.getArgument(0)); return null; }).when(entityManager).refresh(any());
        // Por defecto la clave es nueva (o no hay): la idempotencia deja correr la operacion.
        idempotenciaService = mock(IdempotenciaService.class);
        when(idempotenciaService.ejecutar(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> inv.<java.util.function.Supplier<Object>>getArgument(4).get());
        service = new ChequeGestionService(chequeService, chequeraService, chequeRepository, chequeraRepository,
                movBancarioRepo, bancoLedgerService, idempotenciaService, entityManager);

        cuenta = new CuentaBancaria(); cuenta.setId(4L);
        chequera = new Chequera(); chequera.setId(1L); chequera.setSiguienteNumero(100L);
        chequera.setRangoHasta(105.0); chequera.setEstado(EstadoChequera.ACTIVA); chequera.setCuentaBancaria(cuenta);

        when(chequeraRepository.lockById(1L)).thenReturn(Optional.of(chequera));
        when(chequeraService.save(any())).thenAnswer(i -> i.getArgument(0));
        when(chequeService.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private javax.persistence.EntityManager entityManager;
    /** Lo que el refresh le pone a la chequera: simula lo que otra transacción dejó en la base. */
    private java.util.function.Consumer<Object> enLaBase;

    private Cheque nuevo(boolean diferido) {
        Cheque c = new Cheque();
        c.setChequera(chequera); c.setTotal(500000.0); c.setDiferido(diferido);
        if (diferido) c.setFechaPago(java.time.LocalDateTime.now().plusDays(30));
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

    // ── La chequera se lee de la base y el cheque se valida en el central (issue #376) ──

    private void rechaza(Cheque c, String texto) {
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.emitir(c, null));
        assertTrue(e.getMessage().contains(texto), e.getMessage());
        verify(bancoLedgerService, never()).registrar(anyLong(), any(), any(), any(), any(), any(), any());
        verify(bancoLedgerService, never()).ajustarReservado(anyLong(), any());
        verify(chequeService, never()).save(any());
    }

    @Test
    void el_numero_sale_de_la_base_y_no_de_la_chequera_que_ya_estaba_cargada() {
        // Otra emisión commiteó el 100 mientras esta esperaba el lock: la instancia cargada todavía dice 100.
        enLaBase = x -> ((Chequera) x).setSiguienteNumero(101L);

        Cheque c = service.emitir(nuevo(false), null);

        assertEquals(101.0, c.getNumero());
        assertEquals(102L, chequera.getSiguienteNumero());
        org.mockito.InOrder orden = inOrder(chequeraRepository, entityManager);
        orden.verify(chequeraRepository).lockById(1L);
        orden.verify(entityManager).refresh(chequera);
    }

    @Test
    void una_chequera_que_otro_agoto_mientras_se_esperaba_ya_no_emite() {
        enLaBase = x -> ((Chequera) x).setEstado(EstadoChequera.AGOTADA);

        rechaza(nuevo(false), "no está activa");
    }

    @Test
    void un_total_nulo_cero_o_negativo_se_rechaza() {
        for (Double total : new Double[]{null, 0.0, -500.0, Double.NaN}) {
            Cheque c = nuevo(false);
            c.setTotal(total);
            rechaza(c, "mayor a cero");
        }
        Cheque conClave = nuevo(false);
        conClave.setTotal(Double.NaN);
        assertThrows(GraphQLException.class, () -> service.emitir(conClave, null, "clave"));
        verify(idempotenciaService, never()).ejecutar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void un_diferido_sin_fecha_de_pago_o_con_una_anterior_a_su_emision_se_rechaza() {
        Cheque sinFecha = nuevo(true);
        sinFecha.setFechaPago(null);
        rechaza(sinFecha, "requiere fecha de pago");

        Cheque vencido = nuevo(true);
        vencido.setFechaPago(java.time.LocalDateTime.now().minusDays(1));
        rechaza(vencido, "anterior a su emisión");
    }

    @Test
    void un_diferido_registrado_con_fecha_retroactiva_se_mide_contra_su_emision_y_no_contra_hoy() {
        // Un cheque que ya se entregó hace diez días, con pago a los cinco: las dos fechas pasaron.
        Cheque c = nuevo(true);
        c.setFechaEntrega(java.time.LocalDateTime.now().minusDays(10));
        c.setFechaPago(java.time.LocalDateTime.now().minusDays(5));

        assertEquals(EstadoCheque.DIFERIDO, service.emitir(c, null).getEstado());
    }

    @Test
    void un_diferido_con_pago_el_mismo_dia_de_la_emision_pasa_aunque_la_hora_sea_anterior() {
        Cheque c = nuevo(true);
        c.setFechaEntrega(java.time.LocalDate.now().atTime(18, 0));
        c.setFechaPago(java.time.LocalDate.now().atStartOfDay());

        assertEquals(EstadoCheque.DIFERIDO, service.emitir(c, null).getEstado());
    }

    @Test
    void la_cuenta_es_la_de_la_chequera() {
        Cheque sinCuenta = nuevo(false);
        assertSame(cuenta, service.emitir(sinCuenta, null).getCuentaBancaria());

        CuentaBancaria otra = new CuentaBancaria(); otra.setId(9L);
        Cheque deOtra = nuevo(false);
        deOtra.setCuentaBancaria(otra);
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.emitir(deOtra, null));
        assertTrue(e.getMessage().contains("no es la de la chequera"), e.getMessage());

        chequera.setCuentaBancaria(null);
        GraphQLException sin = assertThrows(GraphQLException.class, () -> service.emitir(nuevo(false), null));
        assertTrue(sin.getMessage().contains("no tiene una cuenta bancaria"), sin.getMessage());
    }

    @Test
    void la_moneda_del_cheque_tiene_que_ser_la_de_la_cuenta() {
        com.franco.dev.domain.financiero.Moneda gs = new com.franco.dev.domain.financiero.Moneda(); gs.setId(1L);
        com.franco.dev.domain.financiero.Moneda rs = new com.franco.dev.domain.financiero.Moneda(); rs.setId(2L);
        cuenta.setMoneda(gs);

        Cheque enReales = nuevo(false);
        enReales.setMoneda(rs);
        rechaza(enReales, "moneda del cheque");

        Cheque enGuaranies = nuevo(false);
        enGuaranies.setMoneda(gs);
        assertEquals(EstadoCheque.COBRADO, service.emitir(enGuaranies, null).getEstado());
    }

    @Test
    void un_numero_fuera_del_rango_de_la_chequera_no_se_emite() {
        chequera.setRangoDesde(100.0);
        chequera.setSiguienteNumero(106L);   // alguien editó el correlativo por encima del rango
        rechaza(nuevo(false), "no tiene más números (rango 100–105)");

        chequera.setSiguienteNumero(40L);
        rechaza(nuevo(false), "está fuera de su rango (100–105)");
    }

    @Test
    void una_chequera_que_todavia_no_emitio_arranca_en_el_primer_numero_de_su_rango() {
        chequera.setRangoDesde(100.0);
        chequera.setSiguienteNumero(null);

        assertEquals(100.0, service.emitir(nuevo(false), null).getNumero());
    }

    // ── Bloque 5 de la issue #376 ──

    @Test
    void un_cheque_al_dia_queda_con_la_fecha_de_emision_como_fecha_de_pago() {
        Cheque hoy = service.emitir(nuevo(false), null);
        assertNotNull(hoy.getFechaPago());
        assertEquals(java.time.LocalDate.now(), hoy.getFechaPago().toLocalDate());

        Cheque retroactivo = nuevo(false);
        retroactivo.setFechaEntrega(java.time.LocalDateTime.of(2026, 9, 20, 15, 0));
        assertEquals(java.time.LocalDateTime.of(2026, 9, 20, 15, 0), service.emitir(retroactivo, null).getFechaPago());
    }

    @Test
    void un_diferido_conserva_su_fecha_de_pago() {
        Cheque c = nuevo(true);
        java.time.LocalDateTime pago = c.getFechaPago();

        assertEquals(pago, service.emitir(c, null).getFechaPago());
    }

    @Test
    void si_el_numero_ya_existe_en_la_base_el_rechazo_dice_cual_y_de_que_chequera() {
        chequera.setNombre("CHEQUERA A");
        when(chequeService.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("x",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_cheque_chequera_numero\"")));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.emitir(nuevo(false), null));

        assertTrue(e.getMessage().contains("El número 100 de la chequera CHEQUERA A ya existe"), e.getMessage());
    }

    @Test
    void otra_falla_de_integridad_no_se_disfraza_de_numero_repetido() {
        when(chequeService.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("x",
                new RuntimeException("ERROR: null value in column \"total\"")));

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> service.emitir(nuevo(false), null));
    }
}
