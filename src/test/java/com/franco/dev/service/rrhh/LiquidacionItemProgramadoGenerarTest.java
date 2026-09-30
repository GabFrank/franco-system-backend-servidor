package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.LiquidacionItemProgramado;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemProgramadoEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.franco.dev.domain.rrhh.LiquidacionItemProgramado.REFERENCIA_TIPO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * El programado entra en la liquidacion de su periodo por {@code generarBorrador} real: no en otro mes, una
 * sola vez aunque se regenere, antes del tope del convenio, y su item no se edita ni se elimina.
 */
class LiquidacionItemProgramadoGenerarTest {

    private LiquidacionSueldoService service;
    private LiquidacionSueldoRepository repository;
    private CreditoConvenioService convenio;
    private final Map<String, LiquidacionSueldo> liquidaciones = new HashMap<>();
    private final List<LiquidacionItem> items = new ArrayList<>();
    private final AtomicLong secuencia = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        repository = mock(LiquidacionSueldoRepository.class);
        when(repository.save(any())).thenAnswer(i -> {
            LiquidacionSueldo l = i.getArgument(0);
            if (l.getId() == null) l.setId(secuencia.incrementAndGet());
            liquidaciones.put(l.getPeriodo(), l);
            return l;
        });
        when(repository.findByFuncionarioIdAndPeriodo(anyLong(), anyString()))
                .thenAnswer(i -> Optional.ofNullable(liquidaciones.get((String) i.getArgument(1))));
        LiquidacionItemRepository itemRepository = mock(LiquidacionItemRepository.class);
        when(itemRepository.save(any())).thenAnswer(i -> {
            LiquidacionItem it = i.getArgument(0);
            if (it.getId() == null) it.setId(secuencia.incrementAndGet());
            if (!items.contains(it)) items.add(it);
            return it;
        });
        when(itemRepository.findById(anyLong())).thenAnswer(i -> items.stream()
                .filter(it -> it.getId().equals(i.getArgument(0))).findFirst());
        when(itemRepository.findByLiquidacionIdOrderByIdAsc(anyLong())).thenAnswer(i -> items.stream()
                .filter(it -> it.getLiquidacion() != null && it.getLiquidacion().getId().equals(i.getArgument(0)))
                .collect(Collectors.toList()));
        doAnswer(i -> { items.removeIf(it -> it.getId().equals(i.getArgument(0))); return null; })
                .when(itemRepository).deleteById(anyLong());
        when(itemRepository.findReferenciasEnOtrasLiquidaciones(anyString(), anyCollection(), any())).thenReturn(List.of());

        Funcionario f = new Funcionario();
        f.setId(7L);
        f.setSueldo(BigDecimal.valueOf(3_000_000));
        f.setIpsActivo(false);
        FuncionarioService funcionarioService = mock(FuncionarioService.class);
        when(funcionarioService.findById(7L)).thenReturn(Optional.of(f));
        ConfiguracionRrhhService config = mock(ConfiguracionRrhhService.class);
        when(config.getNumber(anyString(), any())).thenAnswer(i -> i.getArgument(1));
        convenio = mock(CreditoConvenioService.class);

        // Un DESCUENTO UNIFORME de 300.000 programado para noviembre.
        LiquidacionItemProgramado uniforme = new LiquidacionItemProgramado();
        uniforme.setId(40L);
        uniforme.setPeriodo("2026-11");
        uniforme.setCodigo("DESCUENTO_MANUAL");
        uniforme.setDescripcion("UNIFORME");
        uniforme.setTipo(LiquidacionItemTipo.DESCUENTO);
        uniforme.setMonto(BigDecimal.valueOf(300_000));
        uniforme.setEstado(LiquidacionItemProgramadoEstado.PENDIENTE);
        LiquidacionItemProgramadoRepository programadoRepository = mock(LiquidacionItemProgramadoRepository.class);
        when(programadoRepository.findByFuncionarioIdAndPeriodoAndEstadoOrderByIdAsc(eq(7L), anyString(), eq(LiquidacionItemProgramadoEstado.PENDIENTE)))
                .thenAnswer(i -> "2026-11".equals(i.getArgument(1)) ? List.of(uniforme) : List.of());
        LiquidacionFinalItemRepository finalItemRepository = mock(LiquidacionFinalItemRepository.class);
        when(finalItemRepository.findReferenciasEnOtrosFiniquitos(anyString(), anyCollection(), any())).thenReturn(List.of());
        ItemProgramadoAplicacionService aplicacion = new ItemProgramadoAplicacionService(programadoRepository,
                itemRepository, finalItemRepository);

        service = new LiquidacionSueldoService(
                repository, itemRepository, funcionarioService, mock(MonedaService.class), config,
                mock(HoraExtraRepository.class), mock(PenalizacionRepository.class), mock(JustificativoRepository.class),
                mock(ValeRepository.class), mock(BonoRepository.class), mock(AguinaldoRepository.class),
                mock(VacacionRepository.class), mock(VacacionVentaRepository.class), mock(PrestamoRepository.class),
                mock(PrestamoCuotaRepository.class), mock(CajaVirtualService.class),
                mock(MovimientoCajaVirtualService.class), mock(UsuarioService.class),
                mock(com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository.class),
                mock(JornadaService.class), convenio, mock(LiquidacionConceptoService.class),
                mock(PlatformTransactionManager.class), mock(PrestamoCuotaDescuentoService.class),
                mock(ValeCuotaDescuentoService.class), aplicacion, mock(javax.persistence.EntityManager.class));
    }

    private List<LiquidacionItem> programados(LiquidacionSueldo liq) {
        return items.stream().filter(it -> it.getLiquidacion() == liq && REFERENCIA_TIPO.equals(it.getReferenciaTipo()))
                .collect(Collectors.toList());
    }

    @Test
    void noSaleEnOctubreYSaleEnNoviembre() {
        LiquidacionSueldo octubre = service.generarBorrador(7L, "2026-10", null);
        assertTrue(programados(octubre).isEmpty());

        LiquidacionSueldo noviembre = service.generarBorrador(7L, "2026-11", null);
        List<LiquidacionItem> p = programados(noviembre);
        assertEquals(1, p.size());
        assertEquals("UNIFORME", p.get(0).getDescripcion());
        assertEquals(40L, p.get(0).getReferenciaId());
        assertFalse(p.get(0).getManual());
        assertEquals(0, BigDecimal.valueOf(300_000).compareTo(noviembre.getTotalDescuentos()));
    }

    @Test
    void regenerarNoLoDuplica() {
        LiquidacionSueldo noviembre = service.generarBorrador(7L, "2026-11", null);
        service.generarBorrador(7L, "2026-11", null);
        service.generarBorrador(7L, "2026-11", null);

        assertEquals(1, programados(noviembre).size());
    }

    @Test
    void elTopeDelConvenioYaLoDescuenta() {
        // Sueldo 3.000.000 - programado 300.000: el convenio solo puede cobrar 2.700.000.
        service.generarBorrador(7L, "2026-11", null);

        verify(convenio).planificar(any(), any(), argThat(d -> d.compareTo(BigDecimal.valueOf(2_700_000)) == 0), any(), any());
    }

    @Test
    void suItemNoSeEditaNiSeElimina() {
        LiquidacionSueldo noviembre = service.generarBorrador(7L, "2026-11", null);
        Long itemId = programados(noviembre).get(0).getId();

        assertThrows(GraphQLException.class, () -> service.eliminarItem(itemId));
        assertThrows(GraphQLException.class,
                () -> service.editarItem(itemId, null, BigDecimal.valueOf(1), null, null));
        assertEquals(1, programados(noviembre).size());
        assertEquals(LiquidacionSueldoEstado.BORRADOR, noviembre.getEstado());
    }
}
