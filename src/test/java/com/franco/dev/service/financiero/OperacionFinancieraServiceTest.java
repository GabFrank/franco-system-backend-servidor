package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.TipoOperacionFinanciera;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.OperacionFinancieraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Operaciones financieras (F4): cada uno de los 5 tipos postea los movimientos correctos. */
class OperacionFinancieraServiceTest {

    private OperacionFinancieraRepository repository;
    private TesoreriaService tesoreriaService;
    private BancoLedgerService bancoLedgerService;
    private MovimientoCajaVirtualRepository movimientoCajaVirtualRepository;
    private MovimientoBancarioRepository movimientoBancarioRepository;
    private OperacionFinancieraService service;

    @BeforeEach
    void setUp() {
        repository = mock(OperacionFinancieraRepository.class);
        tesoreriaService = mock(TesoreriaService.class);
        bancoLedgerService = mock(BancoLedgerService.class);
        movimientoCajaVirtualRepository = mock(MovimientoCajaVirtualRepository.class);
        movimientoBancarioRepository = mock(MovimientoBancarioRepository.class);
        service = new OperacionFinancieraService(repository, tesoreriaService, bancoLedgerService,
                movimientoCajaVirtualRepository, movimientoBancarioRepository);
        when(repository.save(any())).thenAnswer(i -> {
            OperacionFinanciera o = i.getArgument(0);
            if (o.getId() == null) o.setId(1L);
            return o;
        });
    }

    private CajaVirtual caja(long id) { CajaVirtual c = new CajaVirtual(); c.setId(id); return c; }
    private CuentaBancaria cuenta(long id) { CuentaBancaria c = new CuentaBancaria(); c.setId(id); return c; }

    @Test
    void cambio_divisa_postea_egreso_e_ingreso_en_caja() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        op.setCajaMayorOrigen(caja(1)); op.setMontoOrigen(new BigDecimal("100"));
        op.setCajaMayorDestino(caja(1)); op.setMontoDestino(new BigDecimal("14"));

        service.registrar(op, null);

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreriaService, times(2)).registrar(cap.capture());
        assertEquals(CajaVirtualTipoMovimiento.EGRESO, cap.getAllValues().get(0).getTipoMovimiento());
        assertEquals(CajaVirtualTipoMovimiento.INGRESO, cap.getAllValues().get(1).getTipoMovimiento());
        verifyNoInteractions(bancoLedgerService);
    }

    @Test
    void deposito_bancario_egresa_caja_y_acredita_banco() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.DEPOSITO_BANCARIO);
        op.setCajaMayorOrigen(caja(1)); op.setMontoOrigen(new BigDecimal("500"));
        op.setCuentaBancariaDestino(cuenta(9)); op.setMontoDestino(new BigDecimal("500"));

        service.registrar(op, null);

        verify(tesoreriaService, times(1)).registrar(any());
        verify(bancoLedgerService).registrar(eq(9L), eq(MovimientoBancarioTipo.ENTRADA_MANUAL), eq(new BigDecimal("500")), any(), any(), any(), any());
    }

    @Test
    void retiro_bancario_debita_banco_e_ingresa_caja() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.RETIRO_BANCARIO);
        op.setCuentaBancariaOrigen(cuenta(9)); op.setMontoOrigen(new BigDecimal("300"));
        op.setCajaMayorDestino(caja(1)); op.setMontoDestino(new BigDecimal("300"));

        service.registrar(op, null);

        verify(bancoLedgerService).registrar(eq(9L), eq(MovimientoBancarioTipo.SALIDA_MANUAL), eq(new BigDecimal("300")), any(), any(), any(), any());
        verify(tesoreriaService, times(1)).registrar(any());
    }

    @Test
    void transferencia_bancaria_no_toca_caja() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.TRANSFERENCIA_BANCARIA);
        op.setCuentaBancariaOrigen(cuenta(9)); op.setMontoOrigen(new BigDecimal("200"));
        op.setCuentaBancariaDestino(cuenta(8)); op.setMontoDestino(new BigDecimal("200"));

        service.registrar(op, null);

        verify(bancoLedgerService).registrar(eq(9L), eq(MovimientoBancarioTipo.SALIDA_MANUAL), any(), any(), any(), any(), any());
        verify(bancoLedgerService).registrar(eq(8L), eq(MovimientoBancarioTipo.ENTRADA_MANUAL), any(), any(), any(), any(), any());
        verifyNoInteractions(tesoreriaService);
    }

    private static final com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo OP =
            com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo.OPERACION_FINANCIERA;

    private OperacionFinanciera operacionBloqueada(long id, Boolean anulado) {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setId(id);
        op.setAnulado(anulado);
        when(repository.lockById(id)).thenReturn(java.util.Optional.of(op));
        return op;
    }

    @Test
    void anular_revierte_patas_de_caja_y_banco_y_marca_anulado() {
        OperacionFinanciera op = operacionBloqueada(5L, false);

        MovimientoCajaVirtual cajaLeg = new MovimientoCajaVirtual();
        MovimientoBancario bancoLeg = new MovimientoBancario();
        when(movimientoCajaVirtualRepository.findByOrigenTipoAndOrigenIdAndActivoTrueOrderByCajaVirtualIdAscIdAsc(
                eq(OP), eq(5L))).thenReturn(java.util.List.of(cajaLeg));
        when(movimientoBancarioRepository.findByOrigenTipoAndOrigenIdAndAnuladoFalseOrderByCuentaBancariaIdAscIdAsc(
                eq("OPERACION_FINANCIERA"), eq(5L))).thenReturn(java.util.List.of(bancoLeg));

        service.anular(5L, "prueba", null);

        // La operacion se toma con lock y el estado se lee despues, antes de revertir nada.
        org.mockito.InOrder orden = inOrder(repository, tesoreriaService, bancoLedgerService);
        orden.verify(repository).lockById(5L);
        orden.verify(repository).findAnuladoById(5L);
        orden.verify(tesoreriaService).revertir(eq(cajaLeg), any(), any());
        orden.verify(bancoLedgerService).revertir(eq(bancoLeg), any(), any());
        assertTrue(op.getAnulado());
        verify(repository).save(op);
    }

    @Test
    void anular_una_operacion_mas_vieja_que_el_limite_se_rechaza_antes_de_revertir_ninguna_pata() {
        // Issue #370: se mide la fecha de la operacion, despues del lock y antes de buscar sus patas.
        OperacionFinanciera op = operacionBloqueada(6L, false);
        java.time.LocalDateTime fecha = java.time.LocalDateTime.now().minusDays(40);
        op.setCreadoEn(fecha);
        org.mockito.Mockito.doThrow(new graphql.GraphQLException("TOPE")).when(tesoreriaService)
                .requireDentroDelLimiteDeAnulacion(eq(fecha), eq("La operación financiera #6"));

        graphql.GraphQLException e = assertThrows(graphql.GraphQLException.class, () -> service.anular(6L, null, null));

        assertEquals("TOPE", e.getMessage());
        verify(tesoreriaService, never()).revertir(any(), any(), any());
        verify(bancoLedgerService, never()).revertir(any(), any(), any());
        assertFalse(op.getAnulado());
        verify(repository, never()).save(any());
    }

    @Test
    void anular_una_operacion_ya_anulada_falla() {
        operacionBloqueada(7L, true);

        graphql.GraphQLException e = assertThrows(graphql.GraphQLException.class, () -> service.anular(7L, null, null));

        assertTrue(e.getMessage().contains("#7") && e.getMessage().contains("ya está anulada"), e.getMessage());
        verify(tesoreriaService, never()).revertir(any(), any(), any());
        verify(bancoLedgerService, never()).revertir(any(), any(), any());
    }

    @Test
    void anular_mira_el_estado_de_la_base_y_no_el_de_la_instancia_ya_cargada() {
        // Otra anulacion commiteo mientras esta esperaba el lock: la instancia sigue diciendo que no.
        OperacionFinanciera op = operacionBloqueada(8L, false);
        when(repository.findAnuladoById(8L)).thenReturn(java.util.Optional.of(true));

        graphql.GraphQLException e = assertThrows(graphql.GraphQLException.class, () -> service.anular(8L, null, null));

        assertTrue(e.getMessage().contains("ya está anulada"), e.getMessage());
        verify(tesoreriaService, never()).revertir(any(), any(), any());
        verify(bancoLedgerService, never()).revertir(any(), any(), any());
        verify(repository, never()).save(op);
    }

    @Test
    void anular_una_operacion_inexistente_lo_dice() {
        graphql.GraphQLException e = assertThrows(graphql.GraphQLException.class, () -> service.anular(404L, null, null));
        assertTrue(e.getMessage().contains("no encontrada"), e.getMessage());
    }

    @Test
    void si_una_pata_ya_esta_revertida_la_anulacion_se_corta_y_la_operacion_no_queda_anulada() {
        OperacionFinanciera op = operacionBloqueada(9L, false);
        MovimientoCajaVirtual cajaLeg = new MovimientoCajaVirtual();
        when(movimientoCajaVirtualRepository.findByOrigenTipoAndOrigenIdAndActivoTrueOrderByCajaVirtualIdAscIdAsc(
                eq(OP), eq(9L))).thenReturn(java.util.List.of(cajaLeg));
        when(tesoreriaService.revertir(eq(cajaLeg), any(), any()))
                .thenThrow(new graphql.GraphQLException("El movimiento #1 ya está anulado."));

        assertThrows(graphql.GraphQLException.class, () -> service.anular(9L, null, null));

        assertFalse(op.getAnulado());
        verify(repository, never()).save(op);
    }

    @Test
    void transferencia_entre_cajas_postea_salida_y_entrada() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.TRANSFERENCIA_ENTRE_CAJAS);
        op.setCajaMayorOrigen(caja(1)); op.setMontoOrigen(new BigDecimal("100"));
        op.setCajaMayorDestino(caja(2)); op.setMontoDestino(new BigDecimal("100"));

        service.registrar(op, null);

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreriaService, times(2)).registrar(cap.capture());
        assertEquals(CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA, cap.getAllValues().get(0).getTipoMovimiento());
        assertEquals(CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA, cap.getAllValues().get(1).getTipoMovimiento());
    }
    private CuentaBancaria cuentaConMoneda(long id, Moneda m) {
        CuentaBancaria c = cuenta(id);
        c.setMoneda(m);
        return c;
    }

    private Moneda moneda(long id) { Moneda m = new Moneda(); m.setId(id); return m; }

    /** Un cambio de divisa entre dos cuentas: no toca caja mayor, dos patas bancarias. */
    @Test
    void cambio_divisa_entre_cuentas_bancarias_postea_las_dos_patas() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        op.setCuentaBancariaOrigen(cuenta(8)); op.setMontoOrigen(new BigDecimal("1000"));
        op.setCuentaBancariaDestino(cuenta(9)); op.setMontoDestino(new BigDecimal("140"));

        service.registrar(op, null);

        verify(bancoLedgerService).registrar(eq(8L), eq(MovimientoBancarioTipo.SALIDA_MANUAL), eq(new BigDecimal("1000")), any(), any(), any(), any());
        verify(bancoLedgerService).registrar(eq(9L), eq(MovimientoBancarioTipo.ENTRADA_MANUAL), eq(new BigDecimal("140")), any(), any(), any(), any());
        verifyNoInteractions(tesoreriaService);
    }

    /** Mixto caja→banco: una pata de cada tipo, la de caja primero (orden canónico de lock). */
    @Test
    void cambio_divisa_de_caja_a_cuenta_lockea_la_caja_primero() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        op.setCajaMayorOrigen(caja(1)); op.setMontoOrigen(new BigDecimal("700000"));
        op.setCuentaBancariaDestino(cuenta(9)); op.setMontoDestino(new BigDecimal("100"));

        service.registrar(op, null);

        InOrder orden = inOrder(tesoreriaService, bancoLedgerService);
        orden.verify(tesoreriaService).registrar(any());
        orden.verify(bancoLedgerService).registrar(eq(9L), eq(MovimientoBancarioTipo.ENTRADA_MANUAL), any(), any(), any(), any(), any());
    }

    /** Mixto banco→caja: la caja se toca primero igual, aunque sea el destino. */
    @Test
    void cambio_divisa_de_cuenta_a_caja_tambien_lockea_la_caja_primero() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        op.setCuentaBancariaOrigen(cuenta(9)); op.setMontoOrigen(new BigDecimal("100"));
        op.setCajaMayorDestino(caja(1)); op.setMontoDestino(new BigDecimal("700000"));

        service.registrar(op, null);

        InOrder orden = inOrder(tesoreriaService, bancoLedgerService);
        orden.verify(tesoreriaService).registrar(any());
        orden.verify(bancoLedgerService).registrar(eq(9L), eq(MovimientoBancarioTipo.SALIDA_MANUAL), any(), any(), any(), any(), any());
    }

    /** El retiro bancario toca la caja antes que el banco: mismo orden que el depósito. */
    @Test
    void retiro_bancario_lockea_la_caja_antes_que_el_banco() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.RETIRO_BANCARIO);
        op.setCuentaBancariaOrigen(cuenta(9)); op.setMontoOrigen(new BigDecimal("300"));
        op.setCajaMayorDestino(caja(1)); op.setMontoDestino(new BigDecimal("300"));

        service.registrar(op, null);

        InOrder orden = inOrder(tesoreriaService, bancoLedgerService);
        orden.verify(tesoreriaService).registrar(any());
        orden.verify(bancoLedgerService).registrar(eq(9L), eq(MovimientoBancarioTipo.SALIDA_MANUAL), any(), any(), any(), any(), any());
    }

    /** Un lado no puede ser caja y cuenta a la vez. */
    @Test
    void cambio_divisa_rechaza_caja_y_cuenta_en_el_mismo_lado() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        op.setCajaMayorOrigen(caja(1)); op.setCuentaBancariaOrigen(cuenta(8));
        op.setMontoOrigen(new BigDecimal("100"));
        op.setCajaMayorDestino(caja(1)); op.setMontoDestino(new BigDecimal("14"));

        assertThrows(RuntimeException.class, () -> service.registrar(op, null));
    }

    /** Cambiar una cuenta contra sí misma no es un cambio. */
    @Test
    void cambio_divisa_rechaza_la_misma_cuenta_en_los_dos_lados() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        op.setCuentaBancariaOrigen(cuenta(9)); op.setMontoOrigen(new BigDecimal("100"));
        op.setCuentaBancariaDestino(cuenta(9)); op.setMontoDestino(new BigDecimal("100"));

        assertThrows(RuntimeException.class, () -> service.registrar(op, null));
    }

    /** La moneda de una pata bancaria la manda la cuenta, no lo que llegó en el formulario. */
    @Test
    void la_moneda_de_la_pata_bancaria_se_deriva_de_la_cuenta() {
        Moneda real = moneda(2);
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        op.setCajaMayorOrigen(caja(1)); op.setMontoOrigen(new BigDecimal("700000"));
        op.setMonedaOrigen(moneda(1));
        op.setCuentaBancariaDestino(cuentaConMoneda(9, real)); op.setMontoDestino(new BigDecimal("100"));
        op.setMonedaDestino(moneda(3)); // el cliente mandó cualquier cosa

        service.registrar(op, null);

        assertEquals(real.getId(), op.getMonedaDestino().getId());
    }

    /** Sin caja mayor en ningún lado, la diferencia no tiene dónde imputarse: se rechaza antes. */
    @Test
    void cambio_divisa_entre_cuentas_rechaza_diferencia_imputable() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        op.setCuentaBancariaOrigen(cuenta(8)); op.setMontoOrigen(new BigDecimal("1000"));
        op.setCuentaBancariaDestino(cuenta(9)); op.setMontoDestino(new BigDecimal("140"));
        op.setDiferencia(new BigDecimal("5"));
        op.setDiferenciaDestinoTipo(com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo.GASTO);

        assertThrows(RuntimeException.class, () -> service.registrar(op, null));
        verifyNoInteractions(tesoreriaService);
        verifyNoInteractions(bancoLedgerService);
    }
}
