package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.*;
import com.franco.dev.domain.rrhh.enums.*;
import com.franco.dev.graphql.rrhh.input.LiquidacionFinalGenerarInput;
import com.franco.dev.repository.financiero.CambioRepository;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * El finiquito con un vale y una cuota de prestamo en reales, en guaranies: por {@code generarBorrador},
 * {@code pagar} y {@code anular} reales, con el servicio de cuotas de prestamo y el conversor reales.
 */
class LiquidacionFinalMonedaTest {

    private LiquidacionFinalService service;
    private CambioRepository cambioRepository;
    private final List<LiquidacionFinalItem> guardados = new ArrayList<>();
    private LiquidacionFinal lf;
    private PrestamoCuota cuota;

    @BeforeEach
    void setUp() {
        Moneda gs = moneda(1L, "GUARANI", 0);
        Moneda real = moneda(2L, "REAL", 2);

        LiquidacionFinalRepository repository = mock(LiquidacionFinalRepository.class);
        when(repository.save(any())).thenAnswer(i -> {
            LiquidacionFinal l = i.getArgument(0);
            if (l.getId() == null) l.setId(600L);
            lf = l;
            return l;
        });
        when(repository.findById(600L)).thenAnswer(i -> Optional.ofNullable(lf));
        when(repository.findFirstByFuncionarioIdAndEstadoOrderByCreadoEnDesc(anyLong(), any())).thenReturn(Optional.empty());

        LiquidacionFinalItemRepository itemRepository = mock(LiquidacionFinalItemRepository.class);
        when(itemRepository.save(any())).thenAnswer(i -> {
            LiquidacionFinalItem it = i.getArgument(0);
            if (it.getId() == null) it.setId(2000L + guardados.size());
            guardados.add(it);
            return it;
        });
        when(itemRepository.findByLiquidacionFinalIdOrderByIdAsc(600L)).thenAnswer(i -> new ArrayList<>(guardados));
        when(itemRepository.findById(anyLong())).thenAnswer(i -> guardados.stream()
                .filter(it -> it.getId().equals(i.getArgument(0))).findFirst());
        when(itemRepository.findCuotasDeValeEnOtrosFiniquitos(anyCollection(), any())).thenReturn(List.of());

        Funcionario f = new Funcionario();
        f.setId(7L);
        f.setSueldo(BigDecimal.valueOf(3_000_000));
        f.setMoneda(gs);
        FuncionarioService funcionarioService = mock(FuncionarioService.class);
        when(funcionarioService.findById(7L)).thenReturn(Optional.of(f));

        ConfiguracionRrhhService config = mock(ConfiguracionRrhhService.class);
        when(config.getNumber(anyString(), any())).thenAnswer(i -> i.getArgument(1));

        MonedaService monedaService = mock(MonedaService.class);
        when(monedaService.empresa()).thenReturn(gs);
        when(monedaService.guarani()).thenReturn(gs);
        when(monedaService.findById(1L)).thenReturn(Optional.of(gs));

        cambioRepository = mock(CambioRepository.class);
        Cambio c = new Cambio();
        c.setValorEnGs(1450d);
        when(cambioRepository.findVigenteDelComercio(eq(2L), any())).thenReturn(c);

        Vale vale = new Vale();
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

        LiquidacionItemRepository mensualItems = mock(LiquidacionItemRepository.class);
        PrestamoCuotaDescuentoService prestamoCuotas = new PrestamoCuotaDescuentoService(prestamoCuotaRepository,
                prestamoRepository, mensualItems, itemRepository);
        ValeCuotaDescuentoService valeCuotas = new ValeCuotaDescuentoService(mock(ValeCuotaRepository.class),
                valeRepository, mensualItems, itemRepository);

        CajaVirtualService cajaVirtualService = mock(CajaVirtualService.class);
        when(cajaVirtualService.findById(anyLong())).thenReturn(Optional.of(new CajaVirtual()));
        MovimientoCajaVirtualService movimientos = mock(MovimientoCajaVirtualService.class);
        when(movimientos.registrarMovimiento(any())).thenAnswer(i -> {
            MovimientoCajaVirtual m = i.getArgument(0);
            m.setId(78L);
            return m;
        });

        service = new LiquidacionFinalService(repository, mock(PagoSolicitudDetalleRepository.class), itemRepository,
                funcionarioService, mock(LiquidacionSueldoRepository.class), mock(VacacionRepository.class), config,
                cajaVirtualService, movimientos, monedaService, mock(UsuarioService.class), valeRepository,
                prestamoRepository, prestamoCuotaRepository, mock(ClienteService.class), mock(VentaCreditoRepository.class),
                mock(PenalizacionRepository.class), mock(CreditoConvenioService.class), mock(AguinaldoRepository.class),
                mock(BaseRemunerativaService.class), prestamoCuotas, valeCuotas,
                mock(ItemProgramadoAplicacionService.class), mock(LiquidacionItemProgramadoService.class),
                new ConversorMoneda(new CotizacionService(cambioRepository)));
    }

    private static Moneda moneda(Long id, String denominacion, int decimales) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setDecimales(decimales);
        return m;
    }

    private void generar() {
        LiquidacionFinalGenerarInput in = new LiquidacionFinalGenerarInput();
        in.setFuncionarioId(7L);
        in.setMotivoEgreso(MotivoEgreso.RENUNCIA);
        in.setFechaEgreso(LocalDate.now().toString());
        in.setMonedaId(1L);
        in.setSalarioBase(BigDecimal.valueOf(3_000_000));
        in.setDiasTrabajadosMes(30);
        in.setDiasVacaciones(0);
        in.setAguinaldo(BigDecimal.ZERO);
        in.setPreavisoOtorgado(true);
        in.setDescontarIps(false);
        in.setCobrarConvenios(false);
        in.setDescontarPenalizaciones(false);
        service.generarBorrador(in);
    }

    private LiquidacionFinalItem item(String referenciaTipo) {
        return guardados.stream().filter(it -> referenciaTipo.equals(it.getReferenciaTipo())).findFirst().orElseThrow();
    }

    @Test
    void elValeYLaCuotaEnRealesEntranConvertidos() {
        generar();

        LiquidacionFinalItem v = item("VALE");
        assertEquals(0, new BigDecimal("159500").compareTo(v.getMonto()), "vale en Gs: " + v.getMonto());
        assertEquals(0, new BigDecimal("110.00").compareTo(v.getMontoMoneda()));
        assertEquals(0, new BigDecimal("145000").compareTo(item("CPP_CUOTA").getMonto()));
        // 3.000.000 de salario - 159.500 - 145.000
        assertEquals(0, new BigDecimal("2695500").compareTo(lf.getTotalLiquidado()), "total: " + lf.getTotalLiquidado());
    }

    @Test
    void laCuotaEnRealesSePagaYSeRevierteEnReales() {
        generar();
        lf.setEstado(LiquidacionFinalEstado.APROBADA);

        service.pagar(600L, 1L);
        assertEquals(0, new BigDecimal("100.00").compareTo(cuota.getMontoPagado()), "pagado: " + cuota.getMontoPagado());

        service.anular(600L);
        assertEquals(0, BigDecimal.ZERO.compareTo(cuota.getMontoPagado()), "pagado: " + cuota.getMontoPagado());
    }

    @Test
    void editarElValeConvertidoRecalculaLosReales() {
        generar();
        LiquidacionFinalItem v = item("VALE");

        service.editarItem(v.getId(), null, new BigDecimal("72500"), null, null);

        assertEquals(0, new BigDecimal("50.00").compareTo(v.getMontoMoneda()), "en R$: " + v.getMontoMoneda());
    }

    @Test
    void sinCotizacionNoSeGenera() {
        when(cambioRepository.findVigenteDelComercio(eq(2L), any())).thenReturn(null);
        assertThrows(GraphQLException.class, this::generar);
    }
}
