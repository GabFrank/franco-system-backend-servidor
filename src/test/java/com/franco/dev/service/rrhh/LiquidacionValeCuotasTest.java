package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.ValeCuota;
import com.franco.dev.domain.rrhh.enums.ValeCuotaEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

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
 * Items de la liquidacion mensual para un vale en cuotas, por {@code generarBorrador} real: sale una cuota
 * por periodo y nunca el vale entero; un vale de 1 cuota sale como siempre.
 */
class LiquidacionValeCuotasTest {

    private LiquidacionSueldoService service;
    private ValeRepository valeRepository;
    private ConfiguracionRrhhService config;
    private final List<LiquidacionItem> guardados = new ArrayList<>();
    private final List<ValeCuota> cuotas = new ArrayList<>();
    private Vale uniforme;

    @BeforeEach
    void setUp() {
        LiquidacionSueldoRepository repository = mock(LiquidacionSueldoRepository.class);
        when(repository.save(any())).thenAnswer(i -> {
            LiquidacionSueldo l = i.getArgument(0);
            if (l.getId() == null) l.setId(500L);
            return l;
        });
        when(repository.findByFuncionarioIdAndPeriodo(anyLong(), anyString())).thenReturn(Optional.empty());
        LiquidacionItemRepository itemRepository = mock(LiquidacionItemRepository.class);
        when(itemRepository.save(any())).thenAnswer(i -> { guardados.add(i.getArgument(0)); return i.getArgument(0); });
        when(itemRepository.findCuotasDeValeEnOtrasLiquidaciones(anyCollection(), any())).thenReturn(List.of());

        Funcionario f = new Funcionario();
        f.setId(7L);
        f.setSueldo(BigDecimal.valueOf(3_000_000));
        f.setIpsActivo(false);
        FuncionarioService funcionarioService = mock(FuncionarioService.class);
        when(funcionarioService.findById(7L)).thenReturn(Optional.of(f));

        config = mock(ConfiguracionRrhhService.class);
        when(config.getNumber(anyString(), any())).thenAnswer(i -> i.getArgument(1));

        uniforme = new Vale();
        uniforme.setId(1L);
        uniforme.setEstado(ValeEstado.CONFIRMADO);
        uniforme.setMonto(BigDecimal.valueOf(300_000));
        uniforme.setCantidadCuotas(2);
        uniforme.setEsAdelanto(false);
        cuotas.add(cuota(10L, 1, LocalDate.of(2026, 9, 15)));
        cuotas.add(cuota(11L, 2, LocalDate.of(2026, 10, 15)));
        valeRepository = mock(ValeRepository.class);
        when(valeRepository.findByFuncionarioIdAndEstado(7L, ValeEstado.CONFIRMADO)).thenReturn(List.of(uniforme));

        ValeCuotaRepository cuotaRepository = mock(ValeCuotaRepository.class);
        when(cuotaRepository.findByValeIdOrderByNumeroAsc(1L)).thenAnswer(i -> new ArrayList<>(cuotas));
        LiquidacionFinalItemRepository finalItemRepository = mock(LiquidacionFinalItemRepository.class);
        when(finalItemRepository.findCuotasDeValeEnOtrosFiniquitos(anyCollection(), any())).thenReturn(List.of());
        ValeCuotaDescuentoService valeCuotas = new ValeCuotaDescuentoService(cuotaRepository, valeRepository,
                itemRepository, finalItemRepository);

        service = new LiquidacionSueldoService(
                repository,
                itemRepository,
                funcionarioService,
                mock(MonedaService.class),
                config,
                mock(HoraExtraRepository.class),
                mock(PenalizacionRepository.class),
                mock(JustificativoRepository.class),
                valeRepository,
                mock(BonoRepository.class),
                mock(AguinaldoRepository.class),
                mock(VacacionRepository.class),
                mock(VacacionVentaRepository.class),
                mock(PrestamoRepository.class),
                mock(PrestamoCuotaRepository.class),
                mock(CajaVirtualService.class),
                mock(MovimientoCajaVirtualService.class),
                mock(UsuarioService.class),
                mock(com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository.class),
                mock(JornadaService.class),
                mock(CreditoConvenioService.class),
                mock(LiquidacionConceptoService.class),
                mock(PlatformTransactionManager.class),
                mock(PrestamoCuotaDescuentoService.class),
                valeCuotas,
                mock(javax.persistence.EntityManager.class));
    }

    private ValeCuota cuota(Long id, int numero, LocalDate fecha) {
        ValeCuota c = new ValeCuota();
        c.setId(id);
        c.setVale(uniforme);
        c.setNumero(numero);
        c.setMonto(BigDecimal.valueOf(150_000));
        c.setFechaDescuento(fecha);
        c.setEstado(ValeCuotaEstado.PENDIENTE);
        return c;
    }

    private List<LiquidacionItem> vales(String periodo) {
        guardados.clear();
        service.generarBorrador(7L, periodo, null);
        return guardados.stream()
                .filter(it -> it.getCodigo().startsWith("VALE") || it.getCodigo().startsWith("ADELANTO"))
                .collect(Collectors.toList());
    }

    @Test
    void septiembreLleva1de2YOctubre2de2() {
        List<LiquidacionItem> sep = vales("2026-09");
        assertEquals(1, sep.size());
        assertEquals("VALE 1/2", sep.get(0).getDescripcion());
        assertEquals(0, BigDecimal.valueOf(150_000).compareTo(sep.get(0).getMonto()));
        assertEquals(ValeService.REFERENCIA_CUOTA, sep.get(0).getReferenciaTipo());
        assertEquals(10L, sep.get(0).getReferenciaId());

        cuotas.get(0).setEstado(ValeCuotaEstado.DESCONTADA);
        List<LiquidacionItem> oct = vales("2026-10");
        assertEquals(1, oct.size());
        assertEquals("VALE 2/2", oct.get(0).getDescripcion());

        cuotas.get(1).setEstado(ValeCuotaEstado.DESCONTADA);
        assertTrue(vales("2026-11").isEmpty());
    }

    @Test
    void conCierreDia25ElPeriodoDeSeptiembreTerminaEl25() {
        when(config.getNumber(eq("DIA_CIERRE_MES"), any())).thenReturn(new BigDecimal("25"));
        cuotas.clear();
        cuotas.add(cuota(10L, 1, LocalDate.of(2026, 9, 28)));
        cuotas.add(cuota(11L, 2, LocalDate.of(2026, 10, 28)));

        assertTrue(vales("2026-09").isEmpty());
        assertEquals("VALE 1/2", vales("2026-10").get(0).getDescripcion());
    }

    @Test
    void unValeDeUnaCuotaSaleEnteroComoSiempre() {
        uniforme.setCantidadCuotas(1);

        List<LiquidacionItem> sep = vales("2026-09");

        assertEquals(1, sep.size());
        assertEquals("VALE", sep.get(0).getDescripcion());
        assertEquals("VALE", sep.get(0).getReferenciaTipo());
        assertEquals(0, BigDecimal.valueOf(300_000).compareTo(sep.get(0).getMonto()));
    }
}
