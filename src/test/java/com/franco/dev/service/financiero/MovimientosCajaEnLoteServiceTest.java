package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualVinculo;
import com.franco.dev.service.financiero.MovimientosCajaEnLoteService.Monto;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Movimientos y transferencias de caja en varias monedas, en un solo pedido (issue #376): qué se valida
 * antes de tocar nada, en qué orden se toman los saldos y qué hace un pedido repetido. Que el lote entre
 * entero o no entre lo prueba MovimientosCajaEnLoteIT, contra la base.
 */
class MovimientosCajaEnLoteServiceTest {

    private TesoreriaService tesoreria;
    private TesoreriaSecurityService seguridad;
    private IdempotenciaService idempotencia;
    private CajaVirtualRepository cajaRepository;
    private CajaVirtualSaldoRepository saldoRepository;
    private MonedaRepository monedaRepository;
    private MovimientoCajaVirtualRepository movimientoRepository;
    private MovimientosCajaEnLoteService service;

    private Usuario usuario;
    private long siguienteId = 100;
    private final List<String> huellas = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        tesoreria = mock(TesoreriaService.class);
        seguridad = mock(TesoreriaSecurityService.class);
        idempotencia = mock(IdempotenciaService.class);
        cajaRepository = mock(CajaVirtualRepository.class);
        saldoRepository = mock(CajaVirtualSaldoRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        movimientoRepository = mock(MovimientoCajaVirtualRepository.class);
        service = new MovimientosCajaEnLoteService(tesoreria, seguridad, idempotencia, cajaRepository,
                saldoRepository, monedaRepository, movimientoRepository);

        usuario = new Usuario();
        usuario.setId(410L);

        // La idempotencia real se prueba en IdempotenciaServiceTest; acá corre la acción como un pedido nuevo.
        when(idempotencia.ejecutar(any(), any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
            huellas.add(i.getArgument(2));
            return ((Supplier<Object>) i.getArgument(4)).get();
        });
        for (long id : new long[]{1L, 2L, 3L}) {
            Moneda m = new Moneda();
            m.setId(id);
            when(monedaRepository.findById(id)).thenReturn(Optional.of(m));
        }
        for (long id : new long[]{7L, 9L}) {
            CajaVirtual c = new CajaVirtual();
            c.setId(id);
            when(cajaRepository.findById(id)).thenReturn(Optional.of(c));
        }
        when(saldoRepository.lockByCajaVirtualIdAndMonedaId(anyLong(), anyLong()))
                .thenReturn(Optional.of(new CajaVirtualSaldo()));
        when(tesoreria.registrar(any())).thenAnswer(i -> {
            MovimientoCajaVirtual m = i.getArgument(0);
            m.setId(siguienteId++);
            return m;
        });
        when(tesoreria.transferirYDevolverSalida(any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
            MovimientoCajaVirtual m = new MovimientoCajaVirtual();
            m.setId(siguienteId++);
            return m;
        });
    }

    private static List<Monto> montos(Object... pares) {
        List<Monto> lista = new ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) {
            lista.add(new Monto(((Number) pares[i]).longValue(), ((Number) pares[i + 1]).doubleValue()));
        }
        return lista;
    }

    private GraphQLException rechazo(List<Monto> montos, CajaVirtualTipoMovimiento tipo) {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.registrarMovimientos(7L, tipo, montos, "X", usuario, "clave"));
        // Un pedido inválido no llega ni a tomar la clave, ni un saldo, ni a registrar.
        verify(idempotencia, never()).ejecutar(any(), any(), any(), any(), any(), any(), any());
        verify(saldoRepository, never()).lockByCajaVirtualIdAndMonedaId(anyLong(), anyLong());
        verify(tesoreria, never()).registrar(any());
        return e;
    }

    @Test
    void registra_un_movimiento_por_moneda_por_moneda_ascendente_y_a_nombre_del_usuario_de_la_sesion() {
        assertTrue(service.registrarMovimientos(7L, CajaVirtualTipoMovimiento.INGRESO,
                montos(3, 20, 1, 500000, 2, 300), "FONDEO", usuario, "clave"));

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreria, times(3)).registrar(cap.capture());
        List<MovimientoCajaVirtual> movs = cap.getAllValues();
        assertEquals(Arrays.asList(1L, 2L, 3L), Arrays.asList(
                movs.get(0).getMoneda().getId(), movs.get(1).getMoneda().getId(), movs.get(2).getMoneda().getId()));
        assertEquals(500000.0, movs.get(0).getCantidad());
        for (MovimientoCajaVirtual m : movs) {
            assertEquals(CajaVirtualTipoMovimiento.INGRESO, m.getTipoMovimiento());
            assertEquals(7L, m.getCajaVirtual().getId());
            assertSame(usuario, m.getUsuario());
            assertEquals(OrigenMovimientoTipo.MANUAL, m.getOrigenTipo());
            assertEquals("FONDEO", m.getDescripcion());
        }
    }

    @Test
    void toma_todos_los_saldos_antes_de_registrar_el_primer_movimiento() {
        service.registrarMovimientos(7L, CajaVirtualTipoMovimiento.EGRESO, montos(2, 300, 1, 500000), "X", usuario, "clave");

        InOrder orden = inOrder(seguridad, saldoRepository, tesoreria);
        orden.verify(seguridad).requireEscrituraCaja(7L);
        orden.verify(saldoRepository).ensureRow(7L, 1L);
        orden.verify(saldoRepository).lockByCajaVirtualIdAndMonedaId(7L, 1L);
        orden.verify(saldoRepository).ensureRow(7L, 2L);
        orden.verify(saldoRepository).lockByCajaVirtualIdAndMonedaId(7L, 2L);
        orden.verify(tesoreria, times(2)).registrar(any());
    }

    @Test
    void una_transferencia_toma_los_saldos_por_caja_y_despues_por_moneda_sea_cual_sea_el_sentido() {
        service.transferir(9L, 7L, montos(2, 300, 1, 500000), "PASE", usuario, "clave");

        InOrder orden = inOrder(seguridad, saldoRepository, tesoreria);
        orden.verify(seguridad).requireEscrituraCaja(9L);
        orden.verify(seguridad).requireEscrituraCaja(7L);
        orden.verify(saldoRepository).lockByCajaVirtualIdAndMonedaId(7L, 1L);
        orden.verify(saldoRepository).lockByCajaVirtualIdAndMonedaId(7L, 2L);
        orden.verify(saldoRepository).lockByCajaVirtualIdAndMonedaId(9L, 1L);
        orden.verify(saldoRepository).lockByCajaVirtualIdAndMonedaId(9L, 2L);
        orden.verify(tesoreria).transferirYDevolverSalida(eq(9L), eq(7L), eq(500000.0), any(), eq("PASE"), same(usuario));
        orden.verify(tesoreria).transferirYDevolverSalida(eq(9L), eq(7L), eq(300.0), any(), eq("PASE"), same(usuario));
    }

    @Test
    void si_una_moneda_se_rechaza_la_excepcion_sale_tal_cual_y_no_se_sigue_con_las_demas() {
        doAnswer(i -> { MovimientoCajaVirtual m = i.getArgument(0); m.setId(1L); return m; })
                .doThrow(new GraphQLException("Saldo insuficiente en la caja virtual"))
                .when(tesoreria).registrar(any());

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.registrarMovimientos(7L,
                CajaVirtualTipoMovimiento.EGRESO, montos(1, 500000, 2, 300, 3, 20), "X", usuario, "clave"));

        assertTrue(e.getMessage().contains("Saldo insuficiente"), e.getMessage());
        verify(tesoreria, times(2)).registrar(any());   // la tercera no se intenta
    }

    @Test
    void un_ajuste_lleva_su_signo() {
        service.registrarMovimientos(7L, CajaVirtualTipoMovimiento.AJUSTE, montos(1, -1500.5), "X", usuario, "clave");

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreria).registrar(cap.capture());
        assertEquals(-1500.5, cap.getValue().getCantidad());
    }

    @Test
    void solo_ingresos_egresos_y_ajustes() {
        for (CajaVirtualTipoMovimiento tipo : new CajaVirtualTipoMovimiento[]{
                CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA, CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA,
                CajaVirtualTipoMovimiento.PAGO_PROVEEDOR, null}) {
            assertTrue(rechazo(montos(1, 100), tipo).getMessage().contains("ingresos, egresos y ajustes"));
        }
    }

    @Test
    void pedidos_invalidos_se_rechazan_antes_de_tocar_nada() {
        assertTrue(rechazo(Collections.emptyList(), CajaVirtualTipoMovimiento.INGRESO).getMessage().contains("al menos un monto"));
        assertTrue(rechazo(null, CajaVirtualTipoMovimiento.INGRESO).getMessage().contains("al menos un monto"));
        assertTrue(rechazo(montos(1, 100, 1, 200), CajaVirtualTipoMovimiento.INGRESO).getMessage().contains("más de una vez"));
        assertTrue(rechazo(montos(1, 0), CajaVirtualTipoMovimiento.INGRESO).getMessage().contains("cero"));
        assertTrue(rechazo(montos(1, -0.0), CajaVirtualTipoMovimiento.AJUSTE).getMessage().contains("cero"));
        assertTrue(rechazo(montos(1, -5), CajaVirtualTipoMovimiento.EGRESO).getMessage().contains("mayor que cero"));
        assertTrue(rechazo(montos(1, Double.NaN), CajaVirtualTipoMovimiento.INGRESO).getMessage().contains("inválido"));
        assertTrue(rechazo(montos(1, Double.POSITIVE_INFINITY), CajaVirtualTipoMovimiento.INGRESO).getMessage().contains("inválido"));
        assertTrue(rechazo(montos(1, 1.00001), CajaVirtualTipoMovimiento.INGRESO).getMessage().contains("decimales"));
        assertTrue(rechazo(montos(1, 1e20), CajaVirtualTipoMovimiento.INGRESO).getMessage().contains("fuera de rango"));
        assertTrue(rechazo(Collections.singletonList(new Monto(null, 5.0)), CajaVirtualTipoMovimiento.INGRESO).getMessage().contains("moneda"));
        Object[] once = new Object[22];
        for (int i = 0; i < 11; i++) { once[2 * i] = 50 + i; once[2 * i + 1] = 1; }
        assertTrue(rechazo(montos(once), CajaVirtualTipoMovimiento.INGRESO).getMessage().contains("máximo"));
    }

    @Test
    void una_moneda_que_no_existe_se_rechaza_en_vez_de_registrarse_en_guaranies() {
        when(monedaRepository.findById(99L)).thenReturn(Optional.empty());

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.registrarMovimientos(7L,
                CajaVirtualTipoMovimiento.INGRESO, montos(1, 100, 99, 5), "X", usuario, "clave"));

        assertTrue(e.getMessage().contains("Moneda no encontrada: 99"), e.getMessage());
        verify(tesoreria, never()).registrar(any());
        verify(saldoRepository, never()).lockByCajaVirtualIdAndMonedaId(anyLong(), anyLong());
    }

    @Test
    void sin_usuario_de_sesion_no_se_registra() {
        assertThrows(GraphQLException.class, () -> service.registrarMovimientos(7L,
                CajaVirtualTipoMovimiento.INGRESO, montos(1, 100), "X", null, "clave"));
        assertThrows(GraphQLException.class, () -> service.transferir(7L, 9L, montos(1, 100), "X", null, "clave"));
        verify(idempotencia, never()).ejecutar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void transferir_a_la_misma_caja_o_con_montos_negativos_se_rechaza() {
        assertThrows(GraphQLException.class, () -> service.transferir(7L, 7L, montos(1, 100), "X", usuario, "clave"));
        assertThrows(GraphQLException.class, () -> service.transferir(7L, 9L, montos(1, -100), "X", usuario, "clave"));
        verify(tesoreria, never()).transferirYDevolverSalida(any(), any(), any(), any(), any(), any());
    }

    @Test
    void la_clave_se_toma_antes_que_el_permiso_los_saldos_y_el_registro_y_guarda_el_primer_movimiento() {
        ArgumentCaptor<Function<Long, Long>> idDe = ArgumentCaptor.forClass(Function.class);

        service.registrarMovimientos(7L, CajaVirtualTipoMovimiento.INGRESO, montos(2, 300, 1, 500000), "X", usuario, "clave-1");

        InOrder orden = inOrder(idempotencia, seguridad, saldoRepository, tesoreria);
        orden.verify(idempotencia).ejecutar(eq("clave-1"), eq(MovimientosCajaEnLoteService.OPERACION_MOVIMIENTOS),
                any(), same(usuario), any(), idDe.capture(), any());
        orden.verify(seguridad).requireEscrituraCaja(7L);
        orden.verify(saldoRepository).lockByCajaVirtualIdAndMonedaId(7L, 1L);
        orden.verify(tesoreria, times(2)).registrar(any());
        assertEquals(100L, idDe.getValue().apply(100L));
    }

    @Test
    void la_huella_no_depende_del_orden_de_los_montos_y_si_de_su_contenido() {
        service.registrarMovimientos(7L, CajaVirtualTipoMovimiento.INGRESO, montos(1, 500000, 2, 300), "X", usuario, "c");
        service.registrarMovimientos(7L, CajaVirtualTipoMovimiento.INGRESO, montos(2, 300.0, 1, 500000.00), "X", usuario, "c");
        service.registrarMovimientos(7L, CajaVirtualTipoMovimiento.INGRESO, montos(1, 500000, 2, 301), "X", usuario, "c");
        service.registrarMovimientos(7L, CajaVirtualTipoMovimiento.EGRESO, montos(1, 500000, 2, 300), "X", usuario, "c");
        service.registrarMovimientos(9L, CajaVirtualTipoMovimiento.INGRESO, montos(1, 500000, 2, 300), "X", usuario, "c");
        service.registrarMovimientos(7L, CajaVirtualTipoMovimiento.INGRESO, montos(1, 500000, 2, 300), "Y", usuario, "c");
        service.transferir(7L, 9L, montos(1, 500000, 2, 300), "X", usuario, "c");
        service.transferir(9L, 7L, montos(1, 500000, 2, 300), "X", usuario, "c");

        assertEquals(huellas.get(0), huellas.get(1));
        assertEquals(7, new java.util.HashSet<>(huellas).size(), "cada pedido distinto tiene su huella");
    }

    // ── El pedido repetido ───────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Function<Long, Long> cargar(Runnable pedido) {
        ArgumentCaptor<Function<Long, Long>> cap = ArgumentCaptor.forClass(Function.class);
        pedido.run();
        verify(idempotencia).ejecutar(any(), any(), any(), any(), any(), any(), cap.capture());
        return cap.getValue();
    }

    @Test
    void el_repetido_de_un_lote_que_sigue_activo_devuelve_su_resultado() {
        Function<Long, Long> cargar = cargar(() -> service.registrarMovimientos(7L,
                CajaVirtualTipoMovimiento.INGRESO, montos(1, 100), "X", usuario, "c"));
        when(movimientoRepository.findActivoById(100L)).thenReturn(Optional.of(true));
        when(movimientoRepository.findVinculoById(100L)).thenReturn(Optional.of(new MovimientoCajaVirtualVinculo(
                100L, CajaVirtualTipoMovimiento.INGRESO, OrigenMovimientoTipo.MANUAL, 7L, null, null, 1L, 100.0, null, null)));

        assertEquals(100L, cargar.apply(100L));
    }

    @Test
    void el_repetido_de_un_lote_anulado_se_rechaza() {
        Function<Long, Long> cargar = cargar(() -> service.registrarMovimientos(7L,
                CajaVirtualTipoMovimiento.INGRESO, montos(1, 100), "X", usuario, "c"));
        when(movimientoRepository.findActivoById(100L)).thenReturn(Optional.of(false));

        GraphQLException e = assertThrows(GraphQLException.class, () -> cargar.apply(100L));
        assertTrue(e.getMessage().contains("después fue anulado"), e.getMessage());
    }

    @Test
    void el_repetido_de_una_transferencia_mira_tambien_la_otra_pata() {
        Function<Long, Long> cargar = cargar(() -> service.transferir(7L, 9L, montos(1, 100), "X", usuario, "c"));
        when(movimientoRepository.findActivoById(100L)).thenReturn(Optional.of(true));
        when(movimientoRepository.findVinculoById(100L)).thenReturn(Optional.of(new MovimientoCajaVirtualVinculo(
                100L, CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA, OrigenMovimientoTipo.MANUAL, 7L, 7L, 9L, 1L, 100.0, 101L, null)));
        when(movimientoRepository.findActivoById(101L)).thenReturn(Optional.of(false));

        assertThrows(GraphQLException.class, () -> cargar.apply(100L));
    }

    @Test
    void un_resultado_que_ya_no_existe_no_se_da_por_bueno() {
        Function<Long, Long> cargar = cargar(() -> service.registrarMovimientos(7L,
                CajaVirtualTipoMovimiento.INGRESO, montos(1, 100), "X", usuario, "c"));
        when(movimientoRepository.findActivoById(100L)).thenReturn(Optional.empty());

        assertNull(cargar.apply(100L));   // IdempotenciaService lo convierte en «quedó registrado sin resultado»
    }
}
