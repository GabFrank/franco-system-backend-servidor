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
import com.franco.dev.repository.financiero.MovimientoCajaVirtualVinculo;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

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

    @Test
    void revertir_sin_permiso_sobre_la_caja_no_revela_que_ya_estaba_anulado_ni_toma_el_lock() {
        MovimientoCajaVirtual anulado = egresoDeVale(64L);
        anulado.setActivo(false);
        when(movimientoRepository.findActivoById(64L)).thenReturn(Optional.of(false));
        doThrow(new GraphQLException("Sin permiso sobre la caja")).when(seguridad).requireEscrituraCaja(1L);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.revertir(anulado, "x", null));

        assertTrue(e.getMessage().contains("Sin permiso"), e.getMessage());
        verify(movimientoRepository, never()).lockById(anyLong());
    }

    // ── Transferencias entre cajas: se anulan completas (issue #376) ─────────────────────────────

    private CajaVirtual cajaB;
    private CajaVirtualSaldo saldoB;
    private final java.util.Map<Long, MovimientoCajaVirtual> guardados = new java.util.HashMap<>();
    private long siguienteId = 500;

    /** Segunda caja (id 2, saldo 400) y un repositorio que se comporta como la base: asigna ids y los recuerda. */
    private void conDosCajas() {
        cajaB = new CajaVirtual();
        cajaB.setId(2L);
        cajaB.setNombre("CAJA B");
        cajaB.setPermiteSaldoNegativo(false);
        saldoB = new CajaVirtualSaldo();
        saldoB.setCajaVirtual(cajaB);
        saldoB.setMoneda(gs);
        saldoB.setSaldo(new BigDecimal("400"));
        when(cajaVirtualRepository.findById(2L)).thenReturn(Optional.of(cajaB));
        when(saldoRepository.lockByCajaVirtualIdAndMonedaId(2L, 10L)).thenReturn(Optional.of(saldoB));
        when(monedaRepository.findById(10L)).thenReturn(Optional.of(gs));
        when(movimientoRepository.save(any())).thenAnswer(i -> {
            MovimientoCajaVirtual m = i.getArgument(0);
            if (m.getId() == null) m.setId(siguienteId++);
            guardados.put(m.getId(), m);
            return m;
        });
        when(movimientoRepository.lockById(any())).thenAnswer(i -> Optional.ofNullable(guardados.get(i.<Long>getArgument(0))));
        when(movimientoRepository.findActivoById(any())).thenAnswer(i -> Optional.ofNullable(guardados.get(i.<Long>getArgument(0)))
                .map(m -> !Boolean.FALSE.equals(m.getActivo())));
        when(movimientoRepository.findVinculoById(any())).thenAnswer(i -> Optional.ofNullable(guardados.get(i.<Long>getArgument(0)))
                .map(TesoreriaServiceTest::vinculoDe));
        when(movimientoRepository.existsByOrigenTipoAndOrigenId(eq(OrigenMovimientoTipo.ANULACION), any()))
                .thenAnswer(i -> guardados.values().stream().anyMatch(m -> m.getOrigenTipo() == OrigenMovimientoTipo.ANULACION
                        && i.<Long>getArgument(1).equals(m.getOrigenId())));
    }

    private static MovimientoCajaVirtualVinculo vinculoDe(MovimientoCajaVirtual m) {
        return new MovimientoCajaVirtualVinculo(m.getId(), m.getTipoMovimiento(), m.getOrigenTipo(),
                m.getCajaVirtual().getId(),
                m.getCajaOrigen() != null ? m.getCajaOrigen().getId() : null,
                m.getCajaDestino() != null ? m.getCajaDestino().getId() : null,
                m.getMoneda() != null ? m.getMoneda().getId() : null,
                m.getCantidad(), m.getReferenciaId(), m.getCreadoEn());
    }

    /** Transfiere 300 de la caja 1 (1000) a la 2 (400): quedan 700 y 700. Devuelve [salida, entrada]. */
    private MovimientoCajaVirtual[] transferencia() {
        conDosCajas();
        service.transferir(1L, 2L, 300.0, gs, "PASE", null);
        MovimientoCajaVirtual salida = guardados.values().stream()
                .filter(m -> m.getTipoMovimiento() == CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA).findFirst().get();
        MovimientoCajaVirtual entrada = guardados.values().stream()
                .filter(m -> m.getTipoMovimiento() == CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA).findFirst().get();
        return new MovimientoCajaVirtual[]{salida, entrada};
    }

    private void assertSaldos(String a, String b) {
        assertEquals(0, new BigDecimal(a).compareTo(saldo.getSaldo()), "caja 1: " + saldo.getSaldo());
        assertEquals(0, new BigDecimal(b).compareTo(saldoB.getSaldo()), "caja 2: " + saldoB.getSaldo());
    }

    private long contras() {
        return guardados.values().stream().filter(m -> m.getOrigenTipo() == OrigenMovimientoTipo.ANULACION).count();
    }

    @Test
    void transferir_deja_las_dos_patas_apuntandose_entre_si() {
        MovimientoCajaVirtual[] t = transferencia();

        assertEquals(t[1].getId(), t[0].getReferenciaId());
        assertEquals(t[0].getId(), t[1].getReferenciaId());
        assertSaldos("700", "700");
    }

    @Test
    void anular_la_salida_de_una_transferencia_anula_tambien_la_entrada() {
        MovimientoCajaVirtual[] t = transferencia();

        MovimientoCajaVirtual contra = service.anular(t[0].getId(), "ERROR", null);

        assertSaldos("1000", "400");
        assertEquals(Boolean.FALSE, t[0].getActivo());
        assertEquals(Boolean.FALSE, t[1].getActivo());
        assertEquals(2, contras());
        // Devuelve el contra de la pata que se pidió: es el de la caja que el usuario está mirando.
        assertEquals(t[0].getId(), contra.getOrigenId());
        assertEquals(1L, contra.getCajaVirtual().getId());
    }

    @Test
    void anular_la_entrada_de_una_transferencia_anula_tambien_la_salida() {
        MovimientoCajaVirtual[] t = transferencia();

        MovimientoCajaVirtual contra = service.anular(t[1].getId(), "ERROR", null);

        assertSaldos("1000", "400");
        assertEquals(Boolean.FALSE, t[0].getActivo());
        assertEquals(Boolean.FALSE, t[1].getActivo());
        assertEquals(t[1].getId(), contra.getOrigenId());
        // El contra de la pata que nadie pidió explica por qué aparece.
        MovimientoCajaVirtual contraSalida = guardados.values().stream()
                .filter(m -> t[0].getId().equals(m.getOrigenId())).findFirst().get();
        assertTrue(contraSalida.getDescripcion().contains("TRANSFERENCIA ANULADA JUNTO CON EL MOV #" + t[1].getId()),
                contraSalida.getDescripcion());
    }

    @Test
    void anular_una_transferencia_hecha_de_la_caja_de_id_mayor_a_la_de_id_menor_tambien_la_anula_completa() {
        conDosCajas();
        service.transferir(2L, 1L, 300.0, gs, "PASE", null);   // acá la entrada se registra primero
        assertSaldos("1300", "100");
        MovimientoCajaVirtual salida = guardados.values().stream()
                .filter(m -> m.getTipoMovimiento() == CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA).findFirst().get();
        assertEquals(2L, salida.getCajaVirtual().getId());
        assertNotNull(salida.getReferenciaId());

        service.anular(salida.getId(), "ERROR", null);

        assertSaldos("1000", "400");
        assertEquals(2, contras());
    }

    @Test
    void un_vinculo_mutuo_entre_patas_de_otra_moneda_o_de_otras_cajas_no_alcanza_para_anular() {
        MovimientoCajaVirtual[] t = transferencia();
        Moneda rs = new Moneda();
        rs.setId(11L);
        t[1].setMoneda(rs);
        GraphQLException otraMoneda = assertThrows(GraphQLException.class, () -> service.anular(t[0].getId(), "ERROR", null));
        assertTrue(otraMoneda.getMessage().contains("no se anula a medias"), otraMoneda.getMessage());

        t[1].setMoneda(gs);
        CajaVirtual cajaC = new CajaVirtual();
        cajaC.setId(3L);
        t[1].setCajaDestino(cajaC);
        GraphQLException otraCaja = assertThrows(GraphQLException.class, () -> service.anular(t[0].getId(), "ERROR", null));
        assertTrue(otraCaja.getMessage().contains("no se anula a medias"), otraCaja.getMessage());
        assertEquals(0, contras());
        assertSaldos("700", "700");
    }

    @Test
    void anular_una_transferencia_dos_veces_rechaza_la_segunda_entre_por_la_pata_que_entre() {
        MovimientoCajaVirtual[] t = transferencia();
        service.anular(t[0].getId(), "ERROR", null);

        GraphQLException porLaMisma = assertThrows(GraphQLException.class, () -> service.anular(t[0].getId(), "OTRA", null));
        GraphQLException porLaOtra = assertThrows(GraphQLException.class, () -> service.anular(t[1].getId(), "OTRA", null));

        assertTrue(porLaMisma.getMessage().contains("ya está anulado"), porLaMisma.getMessage());
        assertTrue(porLaOtra.getMessage().contains("ya está anulado"), porLaOtra.getMessage());
        assertEquals(2, contras());
        assertSaldos("1000", "400");
    }

    @Test
    void anular_una_transferencia_toma_los_dos_movimientos_por_id_ascendente_entre_por_la_que_entre() {
        MovimientoCajaVirtual[] t = transferencia();
        Long menor = Math.min(t[0].getId(), t[1].getId());
        Long mayor = Math.max(t[0].getId(), t[1].getId());
        clearInvocations(movimientoRepository);

        service.anular(mayor, "ERROR", null);

        InOrder orden = inOrder(movimientoRepository);
        orden.verify(movimientoRepository).lockById(menor);
        orden.verify(movimientoRepository).lockById(mayor);
        orden.verify(movimientoRepository).findActivoById(mayor);
    }

    @Test
    void anular_una_pata_cuya_otra_mitad_ya_se_habia_anulado_sola_completa_la_anulacion() {
        MovimientoCajaVirtual[] t = transferencia();
        // Lo que pasaba antes de este cambio: alguien anuló solo la entrada.
        service.revertir(t[1], "MITAD", null);
        assertSaldos("700", "400");

        service.anular(t[0].getId(), "ERROR", null);

        assertSaldos("1000", "400");
        assertEquals(2, contras());
    }

    @Test
    void anular_una_pata_cuya_otra_mitad_esta_inactiva_sin_contra_movimiento_se_rechaza() {
        MovimientoCajaVirtual[] t = transferencia();
        t[1].setActivo(false);   // dato roto: inactiva, pero su efecto sigue en el saldo
        clearInvocations(movimientoRepository);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(t[0].getId(), "ERROR", null));

        assertTrue(e.getMessage().contains("no se anula a medias"), e.getMessage());
        verify(movimientoRepository, never()).save(any());
        assertSaldos("700", "700");
    }

    @Test
    void anular_una_pata_de_transferencia_sin_vinculo_se_rechaza_en_vez_de_anular_la_mitad() {
        MovimientoCajaVirtual[] t = transferencia();
        t[0].setReferenciaId(null);   // así quedaron las transferencias anteriores al vínculo
        clearInvocations(movimientoRepository);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(t[0].getId(), "ERROR", null));

        assertTrue(e.getMessage().contains("no se anula a medias"), e.getMessage());
        verify(movimientoRepository, never()).lockById(any());
        verify(movimientoRepository, never()).save(any());
        assertSaldos("700", "700");
    }

    @Test
    void anular_una_pata_de_transferencia_con_origen_vacio_sigue_la_misma_regla() {
        MovimientoCajaVirtual[] t = transferencia();
        t[0].setOrigenTipo(null);
        t[1].setOrigenTipo(null);

        service.anular(t[0].getId(), "ERROR", null);

        assertSaldos("1000", "400");
        assertEquals(2, contras());
    }

    @Test
    void un_vinculo_que_no_es_mutuo_o_no_coincide_no_alcanza_para_anular() {
        MovimientoCajaVirtual[] t = transferencia();
        // Un ingreso cualquiera al que un cliente le escribió el referenciaId de la salida.
        MovimientoCajaVirtual ajeno = mov(CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA, 300);
        ajeno.setCajaVirtual(cajaB);
        ajeno.setCajaOrigen(caja);
        ajeno.setCajaDestino(cajaB);
        ajeno.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
        ajeno.setReferenciaId(t[0].getId());
        ajeno.setId(900L);
        guardados.put(900L, ajeno);

        GraphQLException noMutuo = assertThrows(GraphQLException.class, () -> service.anular(900L, "ERROR", null));
        assertTrue(noMutuo.getMessage().contains("no se anula a medias"), noMutuo.getMessage());

        t[1].setCantidad(299.0);
        GraphQLException otroMonto = assertThrows(GraphQLException.class, () -> service.anular(t[0].getId(), "ERROR", null));
        assertTrue(otroMonto.getMessage().contains("no se anula a medias"), otroMonto.getMessage());
        assertEquals(0, contras());
    }

    @Test
    void anular_una_transferencia_sin_permiso_en_la_otra_caja_lo_dice_y_no_toma_ningun_lock() {
        MovimientoCajaVirtual[] t = transferencia();
        doThrow(new GraphQLException("No tenes permiso para mover plata en esta caja.")).when(seguridad).requireEscrituraCaja(2L);
        clearInvocations(movimientoRepository);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(t[0].getId(), "ERROR", null));

        assertTrue(e.getMessage().contains("en las dos cajas"), e.getMessage());
        verify(movimientoRepository, never()).lockById(any());
        verify(movimientoRepository, never()).save(any());
        assertSaldos("700", "700");
    }

    @Test
    void anular_una_transferencia_cuando_la_caja_destino_ya_gasto_la_plata_dice_cual_caja() {
        MovimientoCajaVirtual[] t = transferencia();
        saldoB.setSaldo(new BigDecimal("100"));   // de los 700 quedaron 100: no alcanza para devolver 300

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(t[0].getId(), "ERROR", null));

        assertTrue(e.getMessage().contains("CAJA B") && e.getMessage().contains("saldo suficiente"), e.getMessage());
    }

    @Test
    void anular_una_transferencia_mide_el_limite_de_dias_sobre_la_pata_mas_vieja() {
        MovimientoCajaVirtual[] t = transferencia();
        com.franco.dev.domain.empresarial.ConfiguracionGeneral config = new com.franco.dev.domain.empresarial.ConfiguracionGeneral();
        config.setDiasLimiteAnulacion(5);
        when(configRepository.findAll()).thenReturn(java.util.Collections.singletonList(config));
        t[0].setCreadoEn(java.time.LocalDateTime.now().minusDays(1));
        t[1].setCreadoEn(java.time.LocalDateTime.now().minusDays(9));
        clearInvocations(movimientoRepository);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(t[0].getId(), "ERROR", null));

        assertTrue(e.getMessage().contains("límite de 5 días"), e.getMessage());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void la_pata_de_transferencia_de_una_operacion_financiera_se_sigue_anulando_desde_su_modulo() {
        MovimientoCajaVirtual[] t = transferencia();
        t[0].setOrigenTipo(OrigenMovimientoTipo.OPERACION_FINANCIERA);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(t[0].getId(), "ERROR", null));

        assertTrue(e.getMessage().contains("proviene de OPERACION_FINANCIERA"), e.getMessage());
    }

    @Test
    void anular_un_ingreso_manual_de_origen_vacio_sigue_anulando_solo_ese_movimiento() {
        conDosCajas();
        MovimientoCajaVirtual ingreso = service.registrar(mov(CajaVirtualTipoMovimiento.INGRESO, 200));
        assertNull(ingreso.getOrigenTipo());

        service.anular(ingreso.getId(), "ERROR", null);

        assertSaldos("1000", "400");
        assertEquals(1, contras());
    }
}
