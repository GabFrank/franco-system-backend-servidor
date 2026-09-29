package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.ValeCuota;
import com.franco.dev.domain.rrhh.enums.ValeCuotaEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.graphql.rrhh.ValeGraphQL;
import com.franco.dev.graphql.rrhh.input.ValeInput;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.repository.rrhh.ValeCuotaRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Vale en cuotas y vale en especie: generacion de las cuotas, cuando quedan congeladas y las puertas que
 * no pueden dejar un vale con una cuota descontada volver a ser pagable o anulable.
 */
class ValeCuotasTest {

    private ValeRepository repository;
    private ValeCuotaRepository cuotaRepository;
    private LiquidacionItemRepository liquidacionItemRepository;
    private LiquidacionFinalItemRepository liquidacionFinalItemRepository;
    private MovimientoCajaVirtualService movimientoCajaVirtualService;
    private ValeService service;

    /** Las cuotas "en la base", por vale. */
    private final List<ValeCuota> cuotas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(ValeRepository.class);
        cuotaRepository = mock(ValeCuotaRepository.class);
        liquidacionItemRepository = mock(LiquidacionItemRepository.class);
        liquidacionFinalItemRepository = mock(LiquidacionFinalItemRepository.class);
        movimientoCajaVirtualService = mock(MovimientoCajaVirtualService.class);
        PagoSolicitudDetalleRepository detalleRepository = mock(PagoSolicitudDetalleRepository.class);
        service = new ValeService(repository, mock(CajaVirtualService.class), movimientoCajaVirtualService,
                mock(UsuarioService.class), detalleRepository, cuotaRepository,
                liquidacionItemRepository, liquidacionFinalItemRepository);

        when(repository.save(any(Vale.class))).thenAnswer(i -> {
            Vale v = i.getArgument(0);
            if (v.getId() == null) v.setId(1L);
            return v;
        });
        when(cuotaRepository.save(any(ValeCuota.class))).thenAnswer(i -> {
            ValeCuota c = i.getArgument(0);
            if (c.getId() == null) c.setId(100L + cuotas.size());
            cuotas.add(c);
            return c;
        });
        when(cuotaRepository.findByValeIdOrderByNumeroAsc(anyLong())).thenAnswer(i -> new ArrayList<>(cuotas));
        doAnswer(i -> { cuotas.clear(); return null; }).when(cuotaRepository).deleteAll(anyIterable());
        when(liquidacionItemRepository.findLiquidacionesVivasConCuotasDeVale(anyCollection())).thenReturn(List.of());
        when(liquidacionFinalItemRepository.findFiniquitosVivosConCuotasDeVale(anyCollection())).thenReturn(List.of());
        when(detalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(anyLong())).thenReturn(List.of());
    }

    private Vale valeEnCuotas(long monto, int n, LocalDate fecha) {
        Vale v = new Vale();
        v.setFuncionario(new Funcionario());
        v.setMonto(BigDecimal.valueOf(monto));
        v.setFecha(fecha);
        v.setCantidadCuotas(n);
        return v;
    }

    private List<BigDecimal> montos() {
        return cuotas.stream().map(ValeCuota::getMonto).collect(Collectors.toList());
    }

    // ─────────────────────────── generacion ───────────────────────────

    @Test
    void uniformeEnDosCuotasDescuentaMitadEsteMesYMitadElSiguiente() {
        service.save(valeEnCuotas(300_000, 2, LocalDate.of(2026, 9, 15)));

        assertEquals(2, cuotas.size());
        assertEquals(List.of(new BigDecimal("150000"), new BigDecimal("150000")), montos());
        assertEquals(LocalDate.of(2026, 9, 15), cuotas.get(0).getFechaDescuento());
        assertEquals(LocalDate.of(2026, 10, 15), cuotas.get(1).getFechaDescuento());
        assertTrue(cuotas.stream().allMatch(c -> c.getEstado() == ValeCuotaEstado.PENDIENTE));
    }

    @Test
    void enGuaraniesLasCuotasSonEnterasYLaUltimaAbsorbeElRedondeo() {
        service.save(valeEnCuotas(100_000, 3, LocalDate.of(2026, 9, 1)));

        assertEquals(List.of(new BigDecimal("33333"), new BigDecimal("33333"), new BigDecimal("33334")), montos());
    }

    @Test
    void unaFecha31NoSaltaNiRepiteMeses() {
        service.save(valeEnCuotas(300_000, 3, LocalDate.of(2026, 1, 31)));

        assertEquals(LocalDate.of(2026, 1, 31), cuotas.get(0).getFechaDescuento());
        assertEquals(LocalDate.of(2026, 2, 28), cuotas.get(1).getFechaDescuento());
        assertEquals(LocalDate.of(2026, 3, 31), cuotas.get(2).getFechaDescuento());
    }

    @Test
    void unValeDeUnaCuotaNoGeneraCuotas() {
        service.save(valeEnCuotas(300_000, 1, LocalDate.of(2026, 9, 15)));

        assertTrue(cuotas.isEmpty());
        verify(cuotaRepository, never()).save(any());
    }

    @Test
    void enSolicitadoSeRegeneranAlEditar() {
        Vale v = service.save(valeEnCuotas(300_000, 2, LocalDate.of(2026, 9, 15)));
        v.setCantidadCuotas(3);

        service.save(v);

        assertEquals(3, cuotas.size());
        verify(cuotaRepository).flush();
    }

    @Test
    void confirmadoConCuotasNoSeRegenera() {
        Vale v = service.save(valeEnCuotas(300_000, 2, LocalDate.of(2026, 9, 15)));
        v.setEstado(ValeEstado.CONFIRMADO);
        v.setMonto(BigDecimal.valueOf(900_000));

        service.save(v);

        assertEquals(List.of(new BigDecimal("150000"), new BigDecimal("150000")), montos());
        verify(cuotaRepository, never()).deleteAll(anyIterable());
    }

    @Test
    void masDeDoceCuotasSeRechaza() {
        assertThrows(GraphQLException.class, () -> service.save(valeEnCuotas(300_000, 13, LocalDate.of(2026, 9, 15))));
    }

    @Test
    void valeEnCuotasSinFechaSeRechaza() {
        assertThrows(GraphQLException.class, () -> service.save(valeEnCuotas(300_000, 2, null)));
    }

    // ─────────────────────────── en especie ───────────────────────────

    @Test
    void enEspecieNaceConfirmadoSinMovimientoDeCaja() {
        Vale v = service.crearEnEspecie(valeEnCuotas(300_000, 2, LocalDate.of(2026, 9, 15)), null);

        assertEquals(ValeEstado.CONFIRMADO, v.getEstado());
        assertTrue(v.getEnEspecie());
        assertNull(v.getCajaVirtualId());
        assertEquals(2, cuotas.size());
        verify(movimientoCajaVirtualService, never()).registrarMovimiento(any(MovimientoCajaVirtual.class));
    }

    @Test
    void unValeEnEspecieNoSeConfirmaContraUnaCaja() {
        Vale v = valeEnCuotas(300_000, 1, LocalDate.of(2026, 9, 15));
        v.setId(1L);
        v.setEnEspecie(true);
        v.setEstado(ValeEstado.SOLICITADO);
        when(repository.findById(1L)).thenReturn(Optional.of(v));

        assertThrows(GraphQLException.class, () -> service.confirmar(1L, 3L, null));
        verify(movimientoCajaVirtualService, never()).registrarMovimiento(any(MovimientoCajaVirtual.class));
    }

    // ─────────────────────────── anular / pago ───────────────────────────

    private Vale confirmadoEnEspecieConCuotas() {
        Vale v = service.crearEnEspecie(valeEnCuotas(300_000, 2, LocalDate.of(2026, 9, 15)), null);
        when(repository.findById(v.getId())).thenReturn(Optional.of(v));
        return v;
    }

    @Test
    void anularConUnaCuotaDescontadaSeRechaza() {
        Vale v = confirmadoEnEspecieConCuotas();
        cuotas.get(0).setEstado(ValeCuotaEstado.DESCONTADA);
        cuotas.get(0).setLiquidacionId(77L);

        GraphQLException ex = assertThrows(GraphQLException.class, () -> service.anular(v.getId()));

        assertTrue(ex.getMessage().contains("1/2"), ex.getMessage());
        assertEquals(ValeEstado.CONFIRMADO, v.getEstado());
    }

    @Test
    void anularConUnaCuotaEnUnBorradorSeRechaza() {
        Vale v = confirmadoEnEspecieConCuotas();
        when(liquidacionItemRepository.findLiquidacionesVivasConCuotasDeVale(anyCollection())).thenReturn(List.of(55L));

        GraphQLException ex = assertThrows(GraphQLException.class, () -> service.anular(v.getId()));

        assertTrue(ex.getMessage().contains("#55"), ex.getMessage());
    }

    @Test
    void anularSinDescuentosAnulaLasCuotas() {
        Vale v = confirmadoEnEspecieConCuotas();

        service.anular(v.getId());

        assertEquals(ValeEstado.ANULADO, v.getEstado());
        assertTrue(cuotas.stream().allMatch(c -> c.getEstado() == ValeCuotaEstado.ANULADA));
    }

    @Test
    void anularElPagoDeTesoreriaConUnaCuotaDescontadaSeRechaza() {
        // Sin el guard el vale volveria a SOLICITADO y tesoreria lo ofreceria de nuevo por el monto entero.
        Vale v = service.save(valeEnCuotas(300_000, 2, LocalDate.of(2026, 9, 15)));
        v.setEstado(ValeEstado.CONFIRMADO);
        v.setSolicitudPagoId(88L);
        cuotas.get(0).setEstado(ValeCuotaEstado.DESCONTADA);
        when(repository.findBySolicitudPagoId(88L)).thenReturn(v);
        SolicitudPago sp = new SolicitudPago();
        sp.setId(88L); sp.setTipo(TipoSolicitudPago.RRHH); sp.setEstado(SolicitudPagoEstado.SOLICITADO);

        assertThrows(GraphQLException.class, () -> service.sincronizarDesdeSolicitudPago(sp));
        assertEquals(ValeEstado.CONFIRMADO, v.getEstado());
    }

    // ─────────────────────────── saldo ───────────────────────────

    @Test
    void elSaldoPendienteEsLaSumaDeLasCuotasPendientes() {
        Vale v = confirmadoEnEspecieConCuotas();
        cuotas.get(0).setEstado(ValeCuotaEstado.DESCONTADA);

        assertEquals(0, new BigDecimal("150000").compareTo(service.saldoPendiente(v)));
    }

    @Test
    void elSaldoPendienteDeUnValeDescontadoEsCero() {
        Vale v = valeEnCuotas(300_000, 1, LocalDate.of(2026, 9, 15));
        v.setEstado(ValeEstado.DESCONTADO);

        assertEquals(0, BigDecimal.ZERO.compareTo(service.saldoPendiente(v)));
    }

    // ─────────────────────────── saveVale (resolver) ───────────────────────────

    private ValeGraphQL resolver() {
        ValeGraphQL r = new ValeGraphQL();
        ReflectionTestUtils.setField(r, "service", service);
        ReflectionTestUtils.setField(r, "seg", mock(RrhhSecurityService.class));
        ReflectionTestUtils.setField(r, "usuarioService", mock(UsuarioService.class));
        return r;
    }

    private ValeInput input(Long id) {
        ValeInput in = new ValeInput();
        in.setId(id);
        in.setMonto(BigDecimal.valueOf(300_000));
        in.setFecha("2026-09-15");
        return in;
    }

    @Test
    void unDesktopViejoQueNoMandaCuotasLasConserva() {
        Vale v = service.save(valeEnCuotas(300_000, 2, LocalDate.of(2026, 9, 15)));
        when(repository.findById(v.getId())).thenReturn(Optional.of(v));
        ValeInput in = input(v.getId());
        in.setObservacion("talle M");

        resolver().saveVale(in);

        assertEquals(2, v.getCantidadCuotas());
        assertEquals(2, cuotas.size());
    }

    @Test
    void saveValeNoDevuelveASolicitadoUnValeEnEspecie() {
        Vale v = confirmadoEnEspecieConCuotas();
        ValeInput in = input(v.getId());
        in.setEstado(ValeEstado.SOLICITADO);

        resolver().saveVale(in);

        assertEquals(ValeEstado.CONFIRMADO, v.getEstado());
    }

    @Test
    void saveValeNoConfirmaSinCajaUnValeNuevoEnCuotas() {
        ValeInput in = input(null);
        in.setCantidadCuotas(2);
        in.setEstado(ValeEstado.CONFIRMADO);

        Vale v = resolver().saveVale(in);

        assertEquals(ValeEstado.SOLICITADO, v.getEstado());
    }

    @Test
    void saveValeNoCambiaElMontoDeUnValeEnCuotasConfirmado() {
        Vale v = confirmadoEnEspecieConCuotas();
        ValeInput in = input(v.getId());
        in.setMonto(BigDecimal.valueOf(900_000));

        assertThrows(GraphQLException.class, () -> resolver().saveVale(in));
        assertEquals(0, BigDecimal.valueOf(300_000).compareTo(v.getMonto()));
    }

    @Test
    void saveValeNoCambiaLasCuotasFueraDeSolicitado() {
        Vale v = confirmadoEnEspecieConCuotas();
        ValeInput in = input(v.getId());
        in.setCantidadCuotas(3);

        assertThrows(GraphQLException.class, () -> resolver().saveVale(in));
    }
}
