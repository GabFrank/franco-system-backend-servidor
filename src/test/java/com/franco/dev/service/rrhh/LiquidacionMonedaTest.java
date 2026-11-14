package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.*;
import com.franco.dev.domain.rrhh.enums.*;
import com.franco.dev.repository.financiero.CambioRepository;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Items en otra moneda dentro de una liquidacion en guaranies, por {@code generarBorrador}, {@code pagar} y
 * {@code anular} reales, con el servicio de cuotas de prestamo y el conversor reales: el vale de R$ 110
 * descuenta su equivalente en Gs (antes, 110 Gs) y la cuota de prestamo se valida, paga y revierte en R$.
 */
class LiquidacionMonedaTest {

    private static final BigDecimal COTIZACION_REAL = BigDecimal.valueOf(1450);

    private LiquidacionSueldoService service;
    private CambioRepository cambioRepository;
    private final List<LiquidacionItem> guardados = new ArrayList<>();
    private LiquidacionSueldo liq;
    private Moneda gs;
    private Moneda real;
    private Vale vale;
    private PrestamoCuota cuota;
    private Funcionario f;

    @BeforeEach
    void setUp() {
        gs = moneda(1L, "GUARANI", "Gs", 0);
        real = moneda(2L, "REAL", "R$", 2);

        LiquidacionSueldoRepository repository = mock(LiquidacionSueldoRepository.class);
        when(repository.save(any())).thenAnswer(i -> {
            LiquidacionSueldo l = i.getArgument(0);
            if (l.getId() == null) l.setId(500L);
            liq = l;
            return l;
        });
        when(repository.findById(500L)).thenAnswer(i -> Optional.ofNullable(liq));
        when(repository.findByFuncionarioIdAndPeriodo(anyLong(), anyString())).thenReturn(Optional.empty());

        LiquidacionItemRepository itemRepository = mock(LiquidacionItemRepository.class);
        when(itemRepository.save(any())).thenAnswer(i -> {
            LiquidacionItem it = i.getArgument(0);
            if (it.getId() == null) it.setId(1000L + guardados.size());
            guardados.add(it);
            return it;
        });
        when(itemRepository.findByLiquidacionIdOrderByIdAsc(500L)).thenAnswer(i -> new ArrayList<>(guardados));
        when(itemRepository.findById(anyLong())).thenAnswer(i -> guardados.stream()
                .filter(it -> it.getId().equals(i.getArgument(0))).findFirst());
        when(itemRepository.findCuotasDeValeEnOtrasLiquidaciones(anyCollection(), any())).thenReturn(List.of());

        f = new Funcionario();
        f.setId(7L);
        f.setSueldo(BigDecimal.valueOf(3_000_000));
        f.setIpsActivo(false);
        f.setMoneda(gs);
        FuncionarioService funcionarioService = mock(FuncionarioService.class);
        when(funcionarioService.findById(7L)).thenReturn(Optional.of(f));

        ConfiguracionRrhhService config = mock(ConfiguracionRrhhService.class);
        when(config.getNumber(anyString(), any())).thenAnswer(i -> i.getArgument(1));

        MonedaService monedaService = mock(MonedaService.class);
        when(monedaService.empresa()).thenReturn(gs);
        when(monedaService.guarani()).thenReturn(gs);
        when(monedaService.findById(1L)).thenReturn(Optional.of(gs));
        when(monedaService.findById(2L)).thenReturn(Optional.of(real));

        cambioRepository = mock(CambioRepository.class);
        when(cambioRepository.findVigenteDelComercio(eq(2L), any())).thenAnswer(i -> cambio(COTIZACION_REAL));
        ConversorMoneda conversor = new ConversorMoneda(new CotizacionService(cambioRepository));

        vale = new Vale();
        vale.setId(1L);
        vale.setEstado(ValeEstado.CONFIRMADO);
        vale.setMonto(new BigDecimal("110.00"));
        vale.setCantidadCuotas(1);
        vale.setEsAdelanto(false);
        vale.setMoneda(real);
        ValeRepository valeRepository = mock(ValeRepository.class);
        when(valeRepository.findByFuncionarioIdAndEstado(7L, ValeEstado.CONFIRMADO)).thenReturn(List.of(vale));

        Prestamo prestamo = new Prestamo();
        prestamo.setId(3L);
        prestamo.setMoneda(real);
        cuota = new PrestamoCuota();
        cuota.setId(30L);
        cuota.setPrestamo(prestamo);
        cuota.setNumero(1);
        cuota.setMonto(new BigDecimal("100.00"));
        cuota.setMontoPagado(BigDecimal.ZERO);
        cuota.setEstado(PrestamoCuotaEstado.PENDIENTE);
        cuota.setFechaVencimiento(LocalDate.now().plusDays(5));
        PrestamoRepository prestamoRepository = mock(PrestamoRepository.class);
        when(prestamoRepository.findByFuncionarioIdOrderByFechaInicioDesc(7L)).thenReturn(List.of(prestamo));
        PrestamoCuotaRepository prestamoCuotaRepository = mock(PrestamoCuotaRepository.class);
        when(prestamoCuotaRepository.findByPrestamoIdOrderByNumeroAsc(3L)).thenReturn(List.of(cuota));
        when(prestamoCuotaRepository.lockById(30L)).thenReturn(Optional.of(cuota));

        LiquidacionFinalItemRepository finalItemRepository = mock(LiquidacionFinalItemRepository.class);
        PrestamoCuotaDescuentoService prestamoCuotas = new PrestamoCuotaDescuentoService(prestamoCuotaRepository,
                prestamoRepository, itemRepository, finalItemRepository);
        ValeCuotaDescuentoService valeCuotas = new ValeCuotaDescuentoService(mock(ValeCuotaRepository.class),
                valeRepository, itemRepository, finalItemRepository);

        CajaVirtualService cajaVirtualService = mock(CajaVirtualService.class);
        when(cajaVirtualService.findById(anyLong())).thenReturn(Optional.of(new CajaVirtual()));
        MovimientoCajaVirtualService movimientos = mock(MovimientoCajaVirtualService.class);
        when(movimientos.registrarMovimiento(any())).thenAnswer(i -> {
            MovimientoCajaVirtual m = i.getArgument(0);
            m.setId(77L);
            return m;
        });

        service = new LiquidacionSueldoService(
                repository, itemRepository, funcionarioService, monedaService, config,
                mock(HoraExtraRepository.class), mock(PenalizacionRepository.class), mock(JustificativoRepository.class),
                valeRepository, mock(BonoRepository.class), mock(AguinaldoRepository.class),
                mock(VacacionRepository.class), mock(VacacionVentaRepository.class),
                prestamoRepository, prestamoCuotaRepository, cajaVirtualService, movimientos,
                mock(UsuarioService.class), mock(com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository.class),
                mock(JornadaService.class), mock(CreditoConvenioService.class), mock(LiquidacionConceptoService.class),
                mock(PlatformTransactionManager.class), prestamoCuotas, valeCuotas,
                mock(ItemProgramadoAplicacionService.class), mock(javax.persistence.EntityManager.class), conversor);
    }

    private static Moneda moneda(Long id, String denominacion, String simbolo, int decimales) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setSimbolo(simbolo);
        m.setDecimales(decimales);
        return m;
    }

    private static Cambio cambio(BigDecimal valorEnGs) {
        Cambio c = new Cambio();
        c.setValorEnGs(valorEnGs.doubleValue());
        return c;
    }

    private LiquidacionItem item(String codigo) {
        return guardados.stream().filter(it -> codigo.equals(it.getCodigo())).findFirst().orElseThrow();
    }

    @Test
    void elValeEnRealesDescuentaSuEquivalenteEnGuaranies() {
        service.generarBorrador(7L, "2026-11", 1L);

        LiquidacionItem v = item("VALE_DESCUENTO");
        assertEquals(0, new BigDecimal("159500").compareTo(v.getMonto()), "monto en Gs: " + v.getMonto());
        assertEquals(0, new BigDecimal("110.00").compareTo(v.getMontoMoneda()));
        assertEquals(0, COTIZACION_REAL.compareTo(v.getCotizacion()));
        assertEquals(real.getId(), v.getMoneda().getId());
        // 3.000.000 - 159.500 (vale) - 145.000 (cuota de prestamo R$ 100)
        assertEquals(0, new BigDecimal("2695500").compareTo(liq.getTotalNeto()), "neto: " + liq.getTotalNeto());

        LiquidacionItem salario = item("SALARIO_BASE");
        assertEquals(gs.getId(), salario.getMoneda().getId());
        assertEquals(0, BigDecimal.ONE.compareTo(salario.getCotizacion()));
        assertEquals(0, salario.getMonto().compareTo(salario.getMontoMoneda()));
    }

    @Test
    void laCuotaDePrestamoEnRealesSePagaYSeRevierteEnReales() {
        service.generarBorrador(7L, "2026-11", 1L);
        liq.setEstado(LiquidacionSueldoEstado.APROBADA);

        service.pagar(500L, 1L);

        assertEquals(0, new BigDecimal("100.00").compareTo(cuota.getMontoPagado()), "pagado: " + cuota.getMontoPagado());
        assertEquals(PrestamoCuotaEstado.PAGADA, cuota.getEstado());

        service.anular(500L);

        assertEquals(0, BigDecimal.ZERO.compareTo(cuota.getMontoPagado()), "pagado: " + cuota.getMontoPagado());
        assertNotEquals(PrestamoCuotaEstado.PAGADA, cuota.getEstado());
    }

    @Test
    void editarUnItemConvertidoRecalculaElMontoEnSuMoneda() {
        service.generarBorrador(7L, "2026-11", 1L);
        LiquidacionItem v = item("VALE_DESCUENTO");

        service.editarItem(v.getId(), null, new BigDecimal("145000"), null, null);

        assertEquals(0, new BigDecimal("100.00").compareTo(v.getMontoMoneda()), "en R$: " + v.getMontoMoneda());
    }

    @Test
    void sinCotizacionNoSeGeneraConUno() {
        when(cambioRepository.findVigenteDelComercio(eq(2L), any())).thenReturn(null);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.generarBorrador(7L, "2026-11", 1L));
        assertTrue(e.getMessage().contains("REAL"), e.getMessage());
    }

    @Test
    void elLoteSinCotizacionAvisaEnVezDeSaltearCallado() {
        when(cambioRepository.findVigenteDelComercio(eq(2L), any())).thenReturn(null);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.generarLote(List.of(7L), "2026-11", 1L));
        assertTrue(e.getMessage().contains("Quedaron sin generar: 1"), e.getMessage());
    }

    @Test
    void unaLiquidacionSinMonedaTomaLaDeLaEmpresa() {
        vale.setMoneda(gs);
        cuota.getPrestamo().setMoneda(gs);

        service.generarBorrador(7L, "2026-11", null);

        assertEquals(gs.getId(), liq.getMoneda().getId());
        assertEquals(0, new BigDecimal("110.00").compareTo(item("VALE_DESCUENTO").getMonto()));
    }
}
