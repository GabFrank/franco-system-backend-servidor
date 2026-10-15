package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Puente maletín ↔ caja mayor: valor desde conteo de cierre + posteo de ingreso/egreso. */
class MaletinTesoreriaServiceTest {

    private MaletinService maletinService;
    private PdvCajaService pdvCajaService;
    private ConteoMonedaService conteoMonedaService;
    private CajaVirtualRepository cajaVirtualRepository;
    private MonedaRepository monedaRepository;
    private TesoreriaService tesoreriaService;
    private BloqueoTransaccionalService bloqueo;
    private com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository movimientoRepository;
    private MaletinTesoreriaService service;

    @BeforeEach
    void setUp() {
        maletinService = mock(MaletinService.class);
        pdvCajaService = mock(PdvCajaService.class);
        conteoMonedaService = mock(ConteoMonedaService.class);
        cajaVirtualRepository = mock(CajaVirtualRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        tesoreriaService = mock(TesoreriaService.class);
        bloqueo = mock(BloqueoTransaccionalService.class);
        movimientoRepository = mock(com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository.class);
        service = new MaletinTesoreriaService(maletinService, pdvCajaService, conteoMonedaService,
                cajaVirtualRepository, monedaRepository, tesoreriaService, bloqueo, movimientoRepository);
    }

    private Moneda moneda(long id) { Moneda m = new Moneda(); m.setId(id); m.setDenominacion("GUARANI"); return m; }
    private MonedaBilletes billete(Moneda m, double valor) {
        MonedaBilletes mb = new MonedaBilletes(); mb.setMoneda(m); mb.setValor(valor); return mb;
    }
    private ConteoMoneda linea(MonedaBilletes mb, double cantidad) {
        ConteoMoneda cm = new ConteoMoneda(); cm.setMonedaBilletes(mb); cm.setCantidad(cantidad); return cm;
    }

    @Test
    void valor_maletin_suma_por_moneda_del_ultimo_cierre() {
        Moneda gs = moneda(1);
        PdvCaja caja = new PdvCaja();
        caja.setSucursalId(3L);
        Conteo cierre = new Conteo(); cierre.setId(99L);
        caja.setConteoCierre(cierre);
        when(pdvCajaService.findLastByMaletinId(5L)).thenReturn(caja);
        // 10 billetes de 100.000 + 3 de 50.000 = 1.150.000
        when(conteoMonedaService.findByConteoId(99L, 3L)).thenReturn(List.of(
                linea(billete(gs, 100000d), 10d),
                linea(billete(gs, 50000d), 3d)));

        List<MaletinTesoreriaService.ValorMaletinItem> items = service.valorMaletin(5L);

        assertEquals(1, items.size());
        assertEquals(0, new BigDecimal("1150000").compareTo(items.get(0).getTotal()));
    }

    @Test
    void valor_maletin_sin_cierre_devuelve_vacio() {
        PdvCaja caja = new PdvCaja(); // sin conteoCierre
        when(pdvCajaService.findLastByMaletinId(5L)).thenReturn(caja);
        assertTrue(service.valorMaletin(5L).isEmpty());
    }

    @Test
    void ingresar_maletin_postea_ingreso_etiquetado() {
        Maletin maletin = new Maletin(); maletin.setId(5L); maletin.setDescripcion("MALETIN A");
        when(maletinService.findById(5L)).thenReturn(Optional.of(maletin));
        when(cajaVirtualRepository.findById(1L)).thenReturn(Optional.of(new CajaVirtual()));
        when(monedaRepository.findById(2L)).thenReturn(Optional.of(moneda(2)));

        service.ingresarMaletin(1L, 5L, 2L, new BigDecimal("1150000"), "llegada", null);

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreriaService).registrar(cap.capture());
        MovimientoCajaVirtual m = cap.getValue();
        assertEquals(CajaVirtualTipoMovimiento.INGRESO, m.getTipoMovimiento());
        assertEquals(OrigenMovimientoTipo.MALETIN, m.getOrigenTipo());
        assertEquals(5L, m.getReferenciaId());
        assertTrue(m.getDescripcion().startsWith("INGRESO MALETIN MALETIN A"));
    }

    @Test
    void egresar_maletin_con_monto_invalido_falla() {
        assertThrows(graphql.GraphQLException.class,
                () -> service.egresarMaletin(1L, 5L, 2L, BigDecimal.ZERO, null, null));
    }

    // ── El cierre de un maletín se ingresa una sola vez (issue #376) ─────────────────────────────

    /** Ingresos activos por moneda, como los vería la base: lo que postea la prueba queda marcado. */
    private final java.util.Map<Long, List<Long>> ingresados = new java.util.HashMap<>();
    private long siguienteId = 500;

    /** Maletín 5, cerrado en la caja 77 de la sucursal 3 con 1.150.000 Gs (moneda 1) y 200 R$ (moneda 2). */
    private void conCierre() {
        Maletin maletin = new Maletin(); maletin.setId(5L); maletin.setDescripcion("MALETIN A");
        when(maletinService.findById(5L)).thenReturn(Optional.of(maletin));
        when(cajaVirtualRepository.findById(1L)).thenReturn(Optional.of(new CajaVirtual()));
        Moneda gs = moneda(1);
        Moneda rs = moneda(2); rs.setDenominacion("REAL");
        when(monedaRepository.findById(1L)).thenReturn(Optional.of(gs));
        when(monedaRepository.findById(2L)).thenReturn(Optional.of(rs));
        PdvCaja caja = new PdvCaja();
        caja.setId(77L);
        caja.setSucursalId(3L);
        Conteo cierre = new Conteo(); cierre.setId(99L);
        caja.setConteoCierre(cierre);
        when(pdvCajaService.findLastByMaletinId(5L)).thenReturn(caja);
        // Los reales primero, a propósito: el ingreso va por moneda ascendente, no en el orden del conteo.
        when(conteoMonedaService.findByConteoId(99L, 3L)).thenReturn(List.of(
                linea(billete(rs, 100d), 2d),
                linea(billete(gs, 100000d), 10d),
                linea(billete(gs, 50000d), 3d)));
        when(movimientoRepository.findIngresosDeCierreDeMaletin(eq(5L), eq(77L), eq(3L), any()))
                .thenAnswer(i -> ingresados.getOrDefault(i.<Long>getArgument(3), java.util.Collections.emptyList()));
        when(tesoreriaService.registrar(any())).thenAnswer(i -> {
            MovimientoCajaVirtual m = i.getArgument(0);
            m.setId(siguienteId++);
            ingresados.put(m.getMoneda().getId(), java.util.Collections.singletonList(m.getId()));
            return m;
        });
    }

    @Test
    void ingresar_el_cierre_postea_por_moneda_ascendente_y_deja_la_marca_de_la_caja() {
        conCierre();

        List<MovimientoCajaVirtual> creados = service.ingresarMaletinCierre(1L, 5L, null, "llegada", null);

        assertEquals(2, creados.size());
        assertEquals(1L, creados.get(0).getMoneda().getId());
        assertEquals(2L, creados.get(1).getMoneda().getId());
        for (MovimientoCajaVirtual m : creados) {
            assertEquals(OrigenMovimientoTipo.MALETIN, m.getOrigenTipo());
            assertEquals(5L, m.getOrigenId());
            assertEquals(77L, m.getReferenciaId());       // la caja de PDV del cierre
            assertEquals(3L, m.getOrigenSucursalId());
        }
        assertEquals(1150000.0, creados.get(0).getCantidad());
    }

    @Test
    void el_mismo_cierre_no_se_ingresa_dos_veces() {
        conCierre();
        service.ingresarMaletinCierre(1L, 5L, List.of(1L, 2L), null, null);
        clearInvocations(tesoreriaService);

        graphql.GraphQLException e = assertThrows(graphql.GraphQLException.class,
                () -> service.ingresarMaletinCierre(1L, 5L, List.of(1L, 2L), null, null));

        assertTrue(e.getMessage().contains("ya se ingresó en GUARANI (movimiento #500), REAL (movimiento #501)"), e.getMessage());
        verify(tesoreriaService, never()).registrar(any());
    }

    @Test
    void si_se_pide_una_moneda_ya_ingresada_junto_con_otra_no_entra_ninguna() {
        conCierre();
        service.ingresarMaletinCierre(1L, 5L, List.of(1L), null, null);
        clearInvocations(tesoreriaService);

        assertThrows(graphql.GraphQLException.class,
                () -> service.ingresarMaletinCierre(1L, 5L, List.of(1L, 2L), null, null));

        verify(tesoreriaService, never()).registrar(any());
    }

    @Test
    void la_moneda_que_todavia_no_entro_se_puede_ingresar_despues() {
        conCierre();
        service.ingresarMaletinCierre(1L, 5L, List.of(1L), null, null);

        List<MovimientoCajaVirtual> creados = service.ingresarMaletinCierre(1L, 5L, List.of(2L), null, null);

        assertEquals(1, creados.size());
        assertEquals(2L, creados.get(0).getMoneda().getId());
    }

    @Test
    void con_todas_las_monedas_se_ingresan_las_que_faltan_y_si_no_falta_ninguna_se_rechaza() {
        conCierre();
        service.ingresarMaletinCierre(1L, 5L, List.of(1L), null, null);

        List<MovimientoCajaVirtual> creados = service.ingresarMaletinCierre(1L, 5L, null, null, null);
        assertEquals(1, creados.size());
        assertEquals(2L, creados.get(0).getMoneda().getId());

        graphql.GraphQLException e = assertThrows(graphql.GraphQLException.class,
                () -> service.ingresarMaletinCierre(1L, 5L, null, null, null));
        assertTrue(e.getMessage().contains("ya se ingresó"), e.getMessage());
    }

    @Test
    void anulado_el_ingreso_el_cierre_se_puede_ingresar_de_nuevo() {
        conCierre();
        service.ingresarMaletinCierre(1L, 5L, List.of(1L), null, null);
        ingresados.remove(1L);   // anulado desde la caja mayor: ya no hay un ingreso activo

        assertEquals(1, service.ingresarMaletinCierre(1L, 5L, List.of(1L), null, null).size());
    }

    @Test
    void el_lock_se_toma_antes_de_resolver_el_cierre() {
        conCierre();

        service.ingresarMaletinCierre(1L, 5L, null, null, null);

        org.mockito.InOrder orden = inOrder(bloqueo, pdvCajaService, movimientoRepository, tesoreriaService);
        orden.verify(bloqueo).tomar("MALETIN_CIERRE:5");
        orden.verify(pdvCajaService).findLastByMaletinId(5L);
        orden.verify(movimientoRepository).findIngresosDeCierreDeMaletin(5L, 77L, 3L, 1L);
        orden.verify(tesoreriaService, times(2)).registrar(any());
    }

    @Test
    void con_el_maletin_en_una_caja_abierta_lo_dice() {
        when(pdvCajaService.findLastByMaletinId(5L)).thenReturn(new PdvCaja());   // sin conteo de cierre

        graphql.GraphQLException e = assertThrows(graphql.GraphQLException.class,
                () -> service.ingresarMaletinCierre(1L, 5L, null, null, null));

        assertTrue(e.getMessage().contains("está en una caja abierta"), e.getMessage());
    }

    @Test
    void valor_maletin_dice_que_monedas_ya_se_ingresaron() {
        conCierre();
        service.ingresarMaletinCierre(1L, 5L, List.of(1L), null, null);

        List<MaletinTesoreriaService.ValorMaletinItem> items = service.valorMaletin(5L);

        assertEquals(Boolean.TRUE, items.get(0).getIngresado());
        assertEquals(Boolean.FALSE, items.get(1).getIngresado());
    }

    @Test
    void los_movimientos_hechos_a_mano_no_llevan_ni_miran_la_marca() {
        conCierre();

        MovimientoCajaVirtual m = service.ingresarMaletin(1L, 5L, 1L, new BigDecimal("1150000"), null, null);

        assertEquals(5L, m.getReferenciaId());
        assertNull(m.getOrigenSucursalId());
        verify(bloqueo, never()).tomar(any());
        verify(movimientoRepository, never()).findIngresosDeCierreDeMaletin(any(), any(), any(), any());
    }
}
