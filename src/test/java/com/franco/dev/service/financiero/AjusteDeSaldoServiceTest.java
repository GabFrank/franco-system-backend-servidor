package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
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
 * Ajustes de saldo con saldo esperado (issue #376): el ajuste por conteo lo calcula el central contra el
 * saldo real, y el bancario se rechaza si el saldo ya no es el que el cliente vio.
 */
class AjusteDeSaldoServiceTest {

    private TesoreriaService tesoreria;
    private BancoLedgerService bancoLedger;
    private TesoreriaSecurityService seguridad;
    private IdempotenciaService idempotencia;
    private CajaVirtualRepository cajaRepository;
    private CajaVirtualSaldoRepository saldoRepository;
    private MonedaRepository monedaRepository;
    private CuentaBancariaRepository cuentaRepository;
    private MovimientoBancarioRepository movimientoBancarioRepository;
    private EntityManager em;
    private AjusteDeSaldoService service;

    private Usuario usuario;
    /** Lo que devuelve el lock: puede traer un saldo viejo. El refresh le pone el de la base. */
    private CajaVirtualSaldo saldoCargado;
    private CuentaBancaria cuentaCargada;
    private BigDecimal saldoEnLaBase;
    private final List<String> huellas = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        tesoreria = mock(TesoreriaService.class);
        bancoLedger = mock(BancoLedgerService.class);
        seguridad = mock(TesoreriaSecurityService.class);
        idempotencia = mock(IdempotenciaService.class);
        cajaRepository = mock(CajaVirtualRepository.class);
        saldoRepository = mock(CajaVirtualSaldoRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        cuentaRepository = mock(CuentaBancariaRepository.class);
        movimientoBancarioRepository = mock(MovimientoBancarioRepository.class);
        em = mock(EntityManager.class);
        service = new AjusteDeSaldoService(tesoreria, bancoLedger, seguridad, idempotencia, cajaRepository,
                saldoRepository, monedaRepository, cuentaRepository, movimientoBancarioRepository);
        service.setEntityManager(em);

        usuario = new Usuario();
        usuario.setId(410L);

        Moneda gs = new Moneda();
        gs.setId(1L);
        when(monedaRepository.findById(1L)).thenReturn(Optional.of(gs));
        CajaVirtual caja = new CajaVirtual();
        caja.setId(7L);
        when(cajaRepository.existsById(7L)).thenReturn(true);
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(caja));

        saldoEnLaBase = new BigDecimal("1000.0000");
        saldoCargado = new CajaVirtualSaldo();
        saldoCargado.setSaldo(new BigDecimal("1000.0000"));
        when(saldoRepository.lockByCajaVirtualIdAndMonedaId(7L, 1L)).thenReturn(Optional.of(saldoCargado));
        cuentaCargada = new CuentaBancaria();
        cuentaCargada.setSaldo(new BigDecimal("1000.0000"));
        when(cuentaRepository.lockById(3L)).thenReturn(Optional.of(cuentaCargada));
        doAnswer(i -> {
            Object entidad = i.getArgument(0);
            if (entidad instanceof CajaVirtualSaldo) ((CajaVirtualSaldo) entidad).setSaldo(saldoEnLaBase);
            if (entidad instanceof CuentaBancaria) ((CuentaBancaria) entidad).setSaldo(saldoEnLaBase);
            return null;
        }).when(em).refresh(any());

        when(tesoreria.registrar(any())).thenAnswer(i -> i.getArgument(0));
        when(bancoLedger.registrar(anyLong(), any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
            MovimientoBancario m = new MovimientoBancario();
            m.setId(55L);
            return m;
        });
        // La idempotencia real se prueba en IdempotenciaServiceTest; acá corre la acción como un pedido nuevo.
        when(idempotencia.ejecutar(any(), any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
            huellas.add(i.getArgument(2));
            return ((Supplier<Object>) i.getArgument(4)).get();
        });
    }

    private MovimientoCajaVirtual ajustePosteado() {
        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreria).registrar(cap.capture());
        return cap.getValue();
    }

    // ── Conteo de caja ───────────────────────────────────────────────────────────────────────────

    @Test
    void el_conteo_postea_la_diferencia_contra_el_saldo_real_con_signo_y_a_nombre_de_la_sesion() {
        service.ajustarCajaPorConteo(7L, 1L, 1000.0, 940.0, usuario);

        MovimientoCajaVirtual ajuste = ajustePosteado();
        assertEquals(CajaVirtualTipoMovimiento.AJUSTE, ajuste.getTipoMovimiento());
        assertEquals(-60.0, ajuste.getCantidad());
        assertSame(usuario, ajuste.getUsuario());
        assertEquals(OrigenMovimientoTipo.MANUAL, ajuste.getOrigenTipo());
        assertEquals(7L, ajuste.getCajaVirtual().getId());
        assertEquals("AJUSTE POR CONTEO DE CAJA (SISTEMA 1.000 / CONTADO 940)", ajuste.getDescripcion());
    }

    @Test
    void el_saldo_esperado_coincide_aunque_llegue_con_otra_escala_y_los_montos_se_leen_como_en_pantalla() {
        saldoEnLaBase = new BigDecimal("1234567.8900");

        service.ajustarCajaPorConteo(7L, 1L, 1234567.89, 19.99, usuario);

        MovimientoCajaVirtual ajuste = ajustePosteado();
        assertEquals(-1234547.9, ajuste.getCantidad());
        assertEquals("AJUSTE POR CONTEO DE CAJA (SISTEMA 1.234.567,89 / CONTADO 19,99)", ajuste.getDescripcion());
    }

    @Test
    void un_sobrante_se_postea_positivo() {
        service.ajustarCajaPorConteo(7L, 1L, 1000.0, 1000.5, usuario);

        assertEquals(0.5, ajustePosteado().getCantidad());
    }

    @Test
    void si_el_saldo_ya_coincide_con_lo_contado_se_rechaza_es_lo_que_recibe_el_reintento() {
        saldoEnLaBase = new BigDecimal("940.0000");   // el primer intento sí había entrado

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.ajustarCajaPorConteo(7L, 1L, 1000.0, 940.0, usuario));

        assertTrue(e.getMessage().contains("ya coincide con lo contado"), e.getMessage());
        verify(tesoreria, never()).registrar(any());
    }

    @Test
    void si_el_saldo_cambio_desde_que_se_abrio_el_conteo_se_rechaza_diciendo_los_dos_saldos() {
        saldoEnLaBase = new BigDecimal("1250.0000");  // entró un movimiento mientras se contaba

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.ajustarCajaPorConteo(7L, 1L, 1000.0, 940.0, usuario));

        assertTrue(e.getMessage().contains("cambió") && e.getMessage().contains("era 1.000")
                && e.getMessage().contains("ahora es 1.250"), e.getMessage());
        verify(tesoreria, never()).registrar(any());
    }

    @Test
    void compara_contra_el_saldo_de_la_base_y_no_contra_el_de_la_instancia_que_devolvio_el_lock() {
        // La instancia cargada todavía dice 1000 (lo que vio el cliente); la base ya dice 1250.
        saldoCargado.setSaldo(new BigDecimal("1000.0000"));
        saldoEnLaBase = new BigDecimal("1250.0000");

        assertThrows(GraphQLException.class, () -> service.ajustarCajaPorConteo(7L, 1L, 1000.0, 940.0, usuario));

        InOrder orden = inOrder(seguridad, saldoRepository, em);
        orden.verify(seguridad).requireEscrituraCaja(7L);
        orden.verify(saldoRepository).ensureRow(7L, 1L);
        orden.verify(saldoRepository).lockByCajaVirtualIdAndMonedaId(7L, 1L);
        orden.verify(em).refresh(saldoCargado);
    }

    @Test
    void lo_contado_con_ruido_de_punto_flotante_se_redondea_en_vez_de_rechazarse() {
        service.ajustarCajaPorConteo(7L, 1L, 1000.0, 12.350000000000001, usuario);

        assertEquals(-987.65, ajustePosteado().getCantidad());
    }

    @Test
    void un_saldo_negativo_se_ajusta_hasta_lo_contado() {
        saldoEnLaBase = new BigDecimal("-300.0000");

        service.ajustarCajaPorConteo(7L, 1L, -300.0, 50.0, usuario);

        assertEquals(350.0, ajustePosteado().getCantidad());
    }

    @Test
    void montos_invalidos_se_rechazan_antes_de_tomar_el_saldo() {
        assertThrows(GraphQLException.class, () -> service.ajustarCajaPorConteo(7L, 1L, 1000.0, -1.0, usuario));
        assertThrows(GraphQLException.class, () -> service.ajustarCajaPorConteo(7L, 1L, 1000.0, Double.NaN, usuario));
        assertThrows(GraphQLException.class, () -> service.ajustarCajaPorConteo(7L, 1L, Double.POSITIVE_INFINITY, 5.0, usuario));
        assertThrows(GraphQLException.class, () -> service.ajustarCajaPorConteo(7L, 1L, null, 5.0, usuario));
        assertThrows(GraphQLException.class, () -> service.ajustarCajaPorConteo(7L, 99L, 1000.0, 5.0, usuario));
        assertThrows(GraphQLException.class, () -> service.ajustarCajaPorConteo(7L, 1L, 1000.0, 1e20, usuario));
        verify(saldoRepository, never()).lockByCajaVirtualIdAndMonedaId(anyLong(), anyLong());
        verify(tesoreria, never()).registrar(any());
    }

    @Test
    void sin_permiso_sobre_la_caja_no_se_toma_el_saldo() {
        doThrow(new GraphQLException("No tenes permiso")).when(seguridad).requireEscrituraCaja(7L);

        assertThrows(GraphQLException.class, () -> service.ajustarCajaPorConteo(7L, 1L, 1000.0, 940.0, usuario));

        verify(saldoRepository, never()).ensureRow(anyLong(), anyLong());
        verify(saldoRepository, never()).lockByCajaVirtualIdAndMonedaId(anyLong(), anyLong());
    }

    // ── Saldo bancario ───────────────────────────────────────────────────────────────────────────

    @Test
    void el_ajuste_bancario_con_el_saldo_esperado_correcto_se_registra() {
        MovimientoBancario r = service.ajustarSaldoBancario(3L, 100.0, true, " comision mal cargada ", 1000.0, "clave", usuario);

        assertEquals(55L, r.getId());
        verify(bancoLedger).registrar(eq(3L), eq(MovimientoBancarioTipo.AJUSTE_POSITIVO), argThat(m -> m.compareTo(new BigDecimal("100")) == 0),
                eq("AJUSTE: COMISION MAL CARGADA"), eq("MANUAL"), isNull(), same(usuario));
    }

    @Test
    void si_el_saldo_de_la_cuenta_ya_no_es_el_esperado_se_rechaza_sin_registrar() {
        saldoEnLaBase = new BigDecimal("1100.0000");   // la instancia cargada sigue en 1000

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.ajustarSaldoBancario(3L, 100.0, true, "MOTIVO", 1000.0, "clave", usuario));

        assertTrue(e.getMessage().contains("cambió") && e.getMessage().contains("era 1.000")
                && e.getMessage().contains("ahora es 1.100") && e.getMessage().contains("puede que ya haya entrado"),
                e.getMessage());
        verify(bancoLedger, never()).registrar(anyLong(), any(), any(), any(), any(), any(), any());
        InOrder orden = inOrder(cuentaRepository, em);
        orden.verify(cuentaRepository).lockById(3L);
        orden.verify(em).refresh(cuentaCargada);
    }

    @Test
    void sin_saldo_esperado_ni_clave_ajusta_como_siempre() {
        saldoEnLaBase = new BigDecimal("77.0000");

        service.ajustarSaldoBancario(3L, 100.0, false, "MOTIVO", null, null, usuario);

        verify(cuentaRepository, never()).lockById(anyLong());
        verify(bancoLedger).registrar(eq(3L), eq(MovimientoBancarioTipo.AJUSTE_NEGATIVO), any(), any(), any(), any(), any());
    }

    @Test
    void la_clave_se_toma_antes_que_la_cuenta() {
        service.ajustarSaldoBancario(3L, 100.0, true, "MOTIVO", 1000.0, "clave-1", usuario);

        InOrder orden = inOrder(idempotencia, cuentaRepository, bancoLedger);
        orden.verify(idempotencia).ejecutar(eq("clave-1"), eq(AjusteDeSaldoService.OPERACION_AJUSTE_BANCARIO),
                any(), same(usuario), any(), any(), any());
        orden.verify(cuentaRepository).lockById(3L);
        orden.verify(bancoLedger).registrar(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void la_huella_cambia_con_cada_dato_del_pedido_y_no_con_espacios_o_mayusculas_del_motivo() {
        service.ajustarSaldoBancario(3L, 100.0, true, "motivo", 1000.0, "c", usuario);
        service.ajustarSaldoBancario(3L, 100.00, true, "  MOTIVO ", 1000.0, "c", usuario);
        service.ajustarSaldoBancario(3L, 101.0, true, "motivo", 1000.0, "c", usuario);
        service.ajustarSaldoBancario(3L, 100.0, false, "motivo", 1000.0, "c", usuario);
        service.ajustarSaldoBancario(3L, 100.0, true, "otro", 1000.0, "c", usuario);
        service.ajustarSaldoBancario(3L, 100.0, true, "motivo", null, "c", usuario);

        assertEquals(huellas.get(0), huellas.get(1));
        assertEquals(5, new java.util.HashSet<>(huellas).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void el_repetido_devuelve_el_ajuste_original_y_si_se_anulo_se_rechaza() {
        ArgumentCaptor<Function<Long, MovimientoBancario>> cargar = ArgumentCaptor.forClass(Function.class);
        service.ajustarSaldoBancario(3L, 100.0, true, "MOTIVO", 1000.0, "clave", usuario);
        verify(idempotencia).ejecutar(any(), any(), any(), any(), any(), any(), cargar.capture());

        MovimientoBancario original = new MovimientoBancario();
        original.setId(55L);
        when(movimientoBancarioRepository.findById(55L)).thenReturn(Optional.of(original));
        when(movimientoBancarioRepository.findAnuladoById(55L)).thenReturn(Optional.of(false));
        assertSame(original, cargar.getValue().apply(55L));

        when(movimientoBancarioRepository.findAnuladoById(55L)).thenReturn(Optional.of(true));
        GraphQLException e = assertThrows(GraphQLException.class, () -> cargar.getValue().apply(55L));
        assertTrue(e.getMessage().contains("después fue anulado"), e.getMessage());
    }

    @Test
    void monto_o_motivo_invalidos_se_rechazan_antes_de_tomar_la_clave() {
        assertThrows(GraphQLException.class, () -> service.ajustarSaldoBancario(3L, 0.0, true, "MOTIVO", 1000.0, "c", usuario));
        assertThrows(GraphQLException.class, () -> service.ajustarSaldoBancario(3L, Double.NaN, true, "MOTIVO", 1000.0, "c", usuario));
        assertThrows(GraphQLException.class, () -> service.ajustarSaldoBancario(3L, 5.0, true, "  ", 1000.0, "c", usuario));
        assertThrows(GraphQLException.class, () -> service.ajustarSaldoBancario(3L, 0.00004, true, "MOTIVO", 1000.0, "c", usuario));
        assertThrows(GraphQLException.class, () -> service.ajustarSaldoBancario(3L, 1e20, true, "MOTIVO", 1000.0, "c", usuario));
        assertThrows(GraphQLException.class, () -> service.ajustarSaldoBancario(3L, 5.0, true, "MOTIVO", Double.NaN, "c", usuario));
        verify(idempotencia, never()).ejecutar(any(), any(), any(), any(), any(), any(), any());
    }
}
