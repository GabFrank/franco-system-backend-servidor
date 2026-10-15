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
import graphql.GraphQLException;
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
    private ComprobanteNumeracionService numeracion;
    private TesoreriaSecurityService seguridad;
    private com.franco.dev.repository.financiero.CajaVirtualConfiguracionRepository cajaConfiguracionRepository;
    private OperacionFinancieraService service;

    @BeforeEach
    void setUp() {
        repository = mock(OperacionFinancieraRepository.class);
        tesoreriaService = mock(TesoreriaService.class);
        bancoLedgerService = mock(BancoLedgerService.class);
        movimientoCajaVirtualRepository = mock(MovimientoCajaVirtualRepository.class);
        movimientoBancarioRepository = mock(MovimientoBancarioRepository.class);
        numeracion = mock(ComprobanteNumeracionService.class);
        seguridad = mock(TesoreriaSecurityService.class);
        cajaConfiguracionRepository = mock(com.franco.dev.repository.financiero.CajaVirtualConfiguracionRepository.class);
        when(seguridad.puedeEscribirCaja(any())).thenReturn(true);
        service = new OperacionFinancieraService(repository, tesoreriaService, bancoLedgerService,
                movimientoCajaVirtualRepository, movimientoBancarioRepository, numeracion, seguridad, cajaConfiguracionRepository);
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

        // La salida va por el registro común; la entrada, por el de envíos (que acepta una caja receptora).
        ArgumentCaptor<MovimientoCajaVirtual> salida = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        ArgumentCaptor<MovimientoCajaVirtual> entrada = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreriaService, times(1)).registrar(salida.capture());
        verify(tesoreriaService, times(1)).registrarEntradaDeEnvio(entrada.capture());
        assertEquals(CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA, salida.getValue().getTipoMovimiento());
        assertEquals(1L, salida.getValue().getCajaVirtual().getId());
        assertEquals(CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA, entrada.getValue().getTipoMovimiento());
        assertEquals(2L, entrada.getValue().getCajaVirtual().getId());
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

    @Test
    void registrar_guarda_el_comprobante_que_resuelve_la_numeracion_y_lo_pide_antes_de_guardar() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        op.setCajaMayorOrigen(caja(1)); op.setMontoOrigen(new BigDecimal("100"));
        op.setCajaMayorDestino(caja(1)); op.setMontoDestino(new BigDecimal("14"));
        op.setNumeroComprobante("  op-9 ");
        when(numeracion.resolver(eq("OPERACION_FINANCIERA"), eq("  op-9 "), any(), eq("una operación financiera")))
                .thenReturn("OP-9");

        service.registrar(op, null);

        assertEquals("OP-9", op.getNumeroComprobante());
        org.mockito.InOrder orden = inOrder(numeracion, repository, tesoreriaService);
        orden.verify(numeracion).resolver(eq("OPERACION_FINANCIERA"), eq("  op-9 "), any(), eq("una operación financiera"));
        orden.verify(repository).save(any());
        orden.verify(tesoreriaService, atLeastOnce()).registrar(any());
    }

    @Test
    void registrar_con_un_comprobante_repetido_no_guarda_ni_mueve_nada() {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        op.setCajaMayorOrigen(caja(1)); op.setMontoOrigen(new BigDecimal("100"));
        op.setCajaMayorDestino(caja(1)); op.setMontoDestino(new BigDecimal("14"));
        when(numeracion.resolver(any(), any(), any(), any()))
                .thenThrow(new graphql.GraphQLException("Ya existe una operación financiera con el comprobante OP-9."));

        assertThrows(graphql.GraphQLException.class, () -> service.registrar(op, null));

        verify(repository, never()).save(any());
        verifyNoInteractions(tesoreriaService, bancoLedgerService);
    }

    // ── Rol acotado (TESORERIA CAJA): depósito a cuenta habilitada o transferencia entre sus cajas ──

    private OperacionFinanciera deposito(long cajaId, long cuentaId) {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.DEPOSITO_BANCARIO);
        op.setCajaMayorOrigen(caja(cajaId)); op.setMontoOrigen(new BigDecimal("500"));
        op.setCuentaBancariaDestino(cuenta(cuentaId)); op.setMontoDestino(new BigDecimal("500"));
        op.setMonedaOrigen(moneda(1));
        op.getCuentaBancariaDestino().setMoneda(moneda(1));
        return op;
    }

    private void nadaAsentado() {
        verify(repository, never()).save(any());
        verify(tesoreriaService, never()).registrar(any());
        verifyNoInteractions(bancoLedgerService);
    }

    @Test
    void acotado_deposita_a_una_cuenta_habilitada_para_su_caja() {
        when(seguridad.operaAcotado()).thenReturn(true);
        when(cajaConfiguracionRepository.esCuentaVisibleDeLaCaja(1L, 9L)).thenReturn(true);

        service.registrar(deposito(1, 9), null);

        verify(seguridad).requireEscrituraCaja(1L);
        verify(tesoreriaService).registrar(any());
        verify(bancoLedgerService).registrar(eq(9L), eq(MovimientoBancarioTipo.ENTRADA_MANUAL), any(), any(), any(), any(), any());
    }

    @Test
    void acotado_no_deposita_a_una_cuenta_que_no_esta_habilitada_y_no_asienta_nada() {
        when(seguridad.operaAcotado()).thenReturn(true);
        when(cajaConfiguracionRepository.esCuentaVisibleDeLaCaja(1L, 9L)).thenReturn(false);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.registrar(deposito(1, 9), null));

        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        nadaAsentado();
    }

    @Test
    void acotado_sin_escritura_en_la_caja_no_asienta_nada() {
        when(seguridad.operaAcotado()).thenReturn(true);
        doThrow(new GraphQLException("No tenes permiso para mover plata en esta caja.")).when(seguridad).requireEscrituraCaja(1L);

        assertThrows(GraphQLException.class, () -> service.registrar(deposito(1, 9), null));

        nadaAsentado();
    }

    @Test
    void acotado_no_registra_los_tipos_que_mueven_banco_sin_caja_o_sacan_del_banco() {
        when(seguridad.operaAcotado()).thenReturn(true);
        for (TipoOperacionFinanciera tipo : new TipoOperacionFinanciera[]{TipoOperacionFinanciera.CAMBIO_DIVISA,
                TipoOperacionFinanciera.RETIRO_BANCARIO, TipoOperacionFinanciera.TRANSFERENCIA_BANCARIA}) {
            OperacionFinanciera op = deposito(1, 9);
            op.setTipoOperacion(tipo);
            op.setCuentaBancariaOrigen(cuenta(8));
            op.setCajaMayorDestino(caja(1));
            // Un retiro va de cuenta a caja: sin cuenta de destino (con ella se rechaza antes, por otra razón).
            if (tipo == TipoOperacionFinanciera.RETIRO_BANCARIO) op.setCuentaBancariaDestino(null);

            GraphQLException e = assertThrows(GraphQLException.class, () -> service.registrar(op, null), tipo.name());
            assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        }
        nadaAsentado();
    }

    @Test
    void acotado_transfiere_entre_cajas_exigiendo_escritura_en_el_origen_y_envio_en_el_destino() {
        when(seguridad.operaAcotado()).thenReturn(true);
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.TRANSFERENCIA_ENTRE_CAJAS);
        op.setCajaMayorOrigen(caja(1)); op.setMontoOrigen(new BigDecimal("100"));
        op.setCajaMayorDestino(caja(2)); op.setMontoDestino(new BigDecimal("100"));
        op.setMonedaOrigen(moneda(1));

        service.registrar(op, null);

        verify(seguridad).requireEscrituraCaja(1L);
        verify(seguridad, never()).requireEscrituraCaja(2L);
        verify(seguridad).requireEnvioACaja(2L);
        verify(tesoreriaService).registrar(any());
        verify(tesoreriaService).registrarEntradaDeEnvio(any());
    }

    @Test
    void acotado_no_registra_una_diferencia() {
        when(seguridad.operaAcotado()).thenReturn(true);
        when(cajaConfiguracionRepository.esCuentaVisibleDeLaCaja(1L, 9L)).thenReturn(true);
        OperacionFinanciera op = deposito(1, 9);
        op.setDiferencia(new BigDecimal("50"));
        op.setDiferenciaDestinoTipo(com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo.GASTO);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.registrar(op, null));

        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        nadaAsentado();
    }

    @Test
    void acotado_no_anula_una_operacion_de_otro_tipo_y_no_revierte_nada() {
        when(seguridad.operaAcotado()).thenReturn(true);
        OperacionFinanciera op = deposito(1, 9);
        op.setId(5L);
        op.setTipoOperacion(TipoOperacionFinanciera.RETIRO_BANCARIO);
        when(repository.lockById(5L)).thenReturn(java.util.Optional.of(op));
        when(repository.findAnuladoById(5L)).thenReturn(java.util.Optional.of(false));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(5L, "x", null));

        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        verify(tesoreriaService, never()).revertir(any(), any(), any());
        verifyNoInteractions(bancoLedgerService);
    }

    @Test
    void acotado_no_anula_un_deposito_de_una_caja_que_no_puede_mover() {
        when(seguridad.operaAcotado()).thenReturn(true);
        OperacionFinanciera op = deposito(1, 9);
        op.setId(5L);
        when(repository.lockById(5L)).thenReturn(java.util.Optional.of(op));
        when(repository.findAnuladoById(5L)).thenReturn(java.util.Optional.of(false));
        doThrow(new GraphQLException("No tenes permiso para mover plata en esta caja.")).when(seguridad).requireEscrituraCaja(1L);

        assertThrows(GraphQLException.class, () -> service.anular(5L, "x", null));

        verify(tesoreriaService, never()).revertir(any(), any(), any());
        verifyNoInteractions(bancoLedgerService);
    }

    @Test
    void acotado_no_deposita_acreditando_al_banco_mas_de_lo_que_sale_de_la_caja() {
        when(seguridad.operaAcotado()).thenReturn(true);
        when(cajaConfiguracionRepository.esCuentaVisibleDeLaCaja(1L, 9L)).thenReturn(true);
        OperacionFinanciera op = deposito(1, 9);
        op.setMontoOrigen(BigDecimal.ONE);
        op.setMontoDestino(new BigDecimal("1000000000"));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.registrar(op, null));

        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        nadaAsentado();
    }

    @Test
    void acotado_deposita_sin_monto_de_destino_y_al_banco_va_lo_que_salio() {
        when(seguridad.operaAcotado()).thenReturn(true);
        when(cajaConfiguracionRepository.esCuentaVisibleDeLaCaja(1L, 9L)).thenReturn(true);
        OperacionFinanciera op = deposito(1, 9);
        op.setMontoDestino(null);

        service.registrar(op, null);

        verify(bancoLedgerService).registrar(eq(9L), eq(MovimientoBancarioTipo.ENTRADA_MANUAL), eq(new BigDecimal("500")), any(), any(), any(), any());
    }

    @Test
    void acotado_no_deposita_en_otra_moneda_ni_con_cotizacion() {
        when(seguridad.operaAcotado()).thenReturn(true);
        when(cajaConfiguracionRepository.esCuentaVisibleDeLaCaja(1L, 9L)).thenReturn(true);

        OperacionFinanciera otraMoneda = deposito(1, 9);
        otraMoneda.setMonedaOrigen(moneda(3));
        assertTrue(assertThrows(GraphQLException.class, () -> service.registrar(otraMoneda, null))
                .getMessage().startsWith("No autorizado"));

        OperacionFinanciera conCotizacion = deposito(1, 9);
        conCotizacion.setCotizacion(new BigDecimal("7300"));
        assertTrue(assertThrows(GraphQLException.class, () -> service.registrar(conCotizacion, null))
                .getMessage().startsWith("No autorizado"));

        nadaAsentado();
    }

    @Test
    void acotado_no_transfiere_acreditando_mas_de_lo_que_sale_ni_cruzando_monedas() {
        when(seguridad.operaAcotado()).thenReturn(true);
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.TRANSFERENCIA_ENTRE_CAJAS);
        op.setCajaMayorOrigen(caja(1)); op.setMontoOrigen(BigDecimal.ONE); op.setMonedaOrigen(moneda(1));
        op.setCajaMayorDestino(caja(2)); op.setMontoDestino(new BigDecimal("1000000"));
        assertTrue(assertThrows(GraphQLException.class, () -> service.registrar(op, null))
                .getMessage().startsWith("No autorizado"));

        op.setMontoDestino(BigDecimal.ONE);
        op.setMonedaDestino(moneda(3));
        assertTrue(assertThrows(GraphQLException.class, () -> service.registrar(op, null))
                .getMessage().startsWith("No autorizado"));

        nadaAsentado();
    }

    // ── Envío a una caja que recibe envíos (sin acceso sobre ella) ──

    private OperacionFinanciera transferencia(long origen, long destino, String sale, String entra) {
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(TipoOperacionFinanciera.TRANSFERENCIA_ENTRE_CAJAS);
        op.setCajaMayorOrigen(caja(origen)); op.setMontoOrigen(new BigDecimal(sale)); op.setMonedaOrigen(moneda(1));
        op.setCajaMayorDestino(caja(destino));
        if (entra != null) op.setMontoDestino(new BigDecimal(entra));
        return op;
    }

    @Test
    void envio_a_una_caja_receptora_sin_acceso_sobre_ella() {
        // El gerente manda a la caja mayor principal (2): puede mover la suya, no la que recibe.
        when(seguridad.operaAcotado()).thenReturn(true);
        when(seguridad.puedeEscribirCaja(2L)).thenReturn(false);

        service.registrar(transferencia(1, 2, "500000", null), null);

        verify(seguridad).requireEscrituraCaja(1L);
        verify(seguridad, atLeastOnce()).requireEnvioACaja(2L);
        ArgumentCaptor<MovimientoCajaVirtual> entrada = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreriaService).registrarEntradaDeEnvio(entrada.capture());
        // Entra lo mismo que salió, aunque el pedido no traiga monto de destino.
        assertEquals(500000d, entrada.getValue().getCantidad());
        assertEquals(2L, entrada.getValue().getCajaVirtual().getId());
    }

    @Test
    void envio_a_una_caja_que_no_recibe_envios_se_rechaza_y_no_asienta_nada() {
        when(seguridad.operaAcotado()).thenReturn(true);
        when(seguridad.puedeEscribirCaja(2L)).thenReturn(false);
        doThrow(new GraphQLException("No tenes permiso para enviar plata a esta caja.")).when(seguridad).requireEnvioACaja(2L);

        assertThrows(GraphQLException.class, () -> service.registrar(transferencia(1, 2, "500000", null), null));

        nadaAsentado();
        verify(tesoreriaService, never()).registrarEntradaDeEnvio(any());
    }

    @Test
    void quien_gestiona_tambien_envia_a_una_receptora_sin_acceso_pero_por_el_mismo_monto_y_moneda() {
        // Sin acceso sobre la caja que recibe, ningún rol puede acreditarle más de lo que sale de la suya.
        when(seguridad.puedeEscribirCaja(2L)).thenReturn(false);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.registrar(transferencia(1, 2, "1", "1000000"), null));
        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());

        OperacionFinanciera otraMoneda = transferencia(1, 2, "100", "100");
        otraMoneda.setMonedaDestino(moneda(3));
        assertThrows(GraphQLException.class, () -> service.registrar(otraMoneda, null));
        nadaAsentado();
        verify(tesoreriaService, never()).registrarEntradaDeEnvio(any());

        service.registrar(transferencia(1, 2, "100", "100"), null);
        verify(tesoreriaService).registrarEntradaDeEnvio(any());
    }

    @Test
    void con_acceso_sobre_las_dos_cajas_la_transferencia_sigue_como_siempre() {
        // Tesorería entre dos cajas suyas: no se le exige mismo monto (no es un envío).
        service.registrar(transferencia(1, 2, "100", "100"), null);

        verify(seguridad, never()).requireEnvioACaja(any());
        verify(tesoreriaService).registrar(any());
        verify(tesoreriaService).registrarEntradaDeEnvio(any());
    }

    @Test
    void quien_envio_no_anula_el_envio_lo_anula_quien_administra_la_caja_que_recibio() {
        when(seguridad.puedeEscribirCaja(2L)).thenReturn(false);
        OperacionFinanciera op = transferencia(1, 2, "500000", "500000");
        op.setId(5L);
        op.getCajaMayorDestino().setNombre("CAJA MAYOR PRINCIPAL");
        when(repository.lockById(5L)).thenReturn(java.util.Optional.of(op));
        when(repository.findAnuladoById(5L)).thenReturn(java.util.Optional.of(false));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(5L, "x", null));

        assertTrue(e.getMessage().contains("CAJA MAYOR PRINCIPAL"), e.getMessage());
        verify(tesoreriaService, never()).revertir(any(), any(), any());
    }

    // ── La moneda no se tuerce con cuentas bancarias que el tipo de operación no usa ──

    @Test
    void una_transferencia_entre_cajas_no_lleva_cuentas_bancarias() {
        // Con una cuenta en dólares de destino, la moneda derivada acreditaría dólares por guaraníes.
        OperacionFinanciera conDestino = transferencia(1, 2, "1000000", "1000000");
        conDestino.setCuentaBancariaDestino(cuentaConMoneda(9, moneda(3)));
        assertThrows(GraphQLException.class, () -> service.registrar(conDestino, null));

        OperacionFinanciera conOrigen = transferencia(1, 2, "1000000", "1000000");
        conOrigen.setCuentaBancariaOrigen(cuentaConMoneda(9, moneda(3)));
        assertThrows(GraphQLException.class, () -> service.registrar(conOrigen, null));

        nadaAsentado();
        verify(tesoreriaService, never()).registrarEntradaDeEnvio(any());
    }

    @Test
    void un_deposito_no_lleva_cuenta_de_origen_ni_un_retiro_cuenta_de_destino() {
        OperacionFinanciera dep = deposito(1, 9);
        dep.setCuentaBancariaOrigen(cuentaConMoneda(8, moneda(3)));
        assertThrows(GraphQLException.class, () -> service.registrar(dep, null));

        OperacionFinanciera ret = new OperacionFinanciera();
        ret.setTipoOperacion(TipoOperacionFinanciera.RETIRO_BANCARIO);
        ret.setCuentaBancariaOrigen(cuenta(9)); ret.setMontoOrigen(new BigDecimal("500"));
        ret.setCajaMayorDestino(caja(1)); ret.setMontoDestino(new BigDecimal("500"));
        ret.setCuentaBancariaDestino(cuentaConMoneda(8, moneda(3)));
        assertThrows(GraphQLException.class, () -> service.registrar(ret, null));

        nadaAsentado();
    }

    @Test
    void en_el_deposito_acotado_la_moneda_se_valida_despues_de_derivarla_de_la_cuenta() {
        // El pedido dice que ambos lados son guaraníes, pero la cuenta es en dólares: manda la cuenta.
        when(seguridad.operaAcotado()).thenReturn(true);
        when(cajaConfiguracionRepository.esCuentaVisibleDeLaCaja(1L, 9L)).thenReturn(true);
        OperacionFinanciera op = deposito(1, 9);
        op.getCuentaBancariaDestino().setMoneda(moneda(3));
        op.setMonedaDestino(moneda(1));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.registrar(op, null));

        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        nadaAsentado();
    }

    @Test
    void un_envio_a_una_caja_que_no_se_administra_no_lleva_diferencia() {
        when(seguridad.puedeEscribirCaja(2L)).thenReturn(false);
        OperacionFinanciera op = transferencia(1, 2, "100", "100");
        op.setDiferencia(new BigDecimal("10"));
        op.setDiferenciaDestinoTipo(com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo.GASTO);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.registrar(op, null));

        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        nadaAsentado();
        verify(tesoreriaService, never()).registrarEntradaDeEnvio(any());
    }
}
