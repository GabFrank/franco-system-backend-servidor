package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.LiquidacionConcepto;
import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.LiquidacionItemProgramado;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemProgramadoEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Programar un ítem desde la liquidación de un periodo para que se aplique en la de otro posterior.
 */
class LiquidacionItemProgramadoServiceTest {

    private LiquidacionItemProgramadoRepository repository;
    private LiquidacionSueldoRepository liquidacionRepository;
    private LiquidacionItemRepository itemRepository;
    private LiquidacionFinalItemRepository finalItemRepository;
    private LiquidacionConceptoService conceptoService;
    private CreditoConvenioService convenio;
    private LiquidacionItemProgramadoService service;

    private LiquidacionSueldo septiembre;
    private final List<LiquidacionItem> items = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(LiquidacionItemProgramadoRepository.class);
        when(repository.save(any())).thenAnswer(i -> {
            LiquidacionItemProgramado p = i.getArgument(0);
            if (p.getId() == null) p.setId(40L);
            return p;
        });
        liquidacionRepository = mock(LiquidacionSueldoRepository.class);
        when(liquidacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        itemRepository = mock(LiquidacionItemRepository.class);
        when(itemRepository.save(any())).thenAnswer(i -> { items.add(i.getArgument(0)); return i.getArgument(0); });
        when(itemRepository.findByLiquidacionIdOrderByIdAsc(anyLong())).thenAnswer(i -> new ArrayList<>(items));
        doAnswer(i -> { items.removeIf(it -> i.getArgument(0).equals(it.getId())); return null; })
                .when(itemRepository).deleteById(anyLong());
        finalItemRepository = mock(LiquidacionFinalItemRepository.class);
        conceptoService = mock(LiquidacionConceptoService.class);
        convenio = mock(CreditoConvenioService.class);

        LiquidacionSueldoService liquidacionService = new LiquidacionSueldoService(
                liquidacionRepository, itemRepository,
                mock(FuncionarioService.class), mock(MonedaService.class), mock(ConfiguracionRrhhService.class),
                mock(HoraExtraRepository.class), mock(PenalizacionRepository.class), mock(JustificativoRepository.class),
                mock(ValeRepository.class), mock(BonoRepository.class), mock(AguinaldoRepository.class),
                mock(VacacionRepository.class), mock(VacacionVentaRepository.class), mock(PrestamoRepository.class),
                mock(PrestamoCuotaRepository.class), mock(CajaVirtualService.class),
                mock(MovimientoCajaVirtualService.class), mock(UsuarioService.class),
                mock(PagoSolicitudDetalleRepository.class), mock(JornadaService.class),
                convenio, conceptoService,
                mock(PlatformTransactionManager.class), mock(PrestamoCuotaDescuentoService.class),
                mock(ValeCuotaDescuentoService.class), mock(com.franco.dev.service.rrhh.ItemProgramadoAplicacionService.class), mock(javax.persistence.EntityManager.class));
        service = new LiquidacionItemProgramadoService(repository, liquidacionRepository, itemRepository,
                finalItemRepository, liquidacionService);

        Funcionario f = new Funcionario();
        f.setId(7L);
        septiembre = liquidacion(500L, "2026-09", LiquidacionSueldoEstado.BORRADOR);
        septiembre.setFuncionario(f);
        when(liquidacionRepository.findById(500L)).thenReturn(Optional.of(septiembre));
        when(liquidacionRepository.findByFuncionarioIdAndPeriodo(anyLong(), anyString())).thenReturn(Optional.empty());
    }

    private static LiquidacionSueldo liquidacion(long id, String periodo, LiquidacionSueldoEstado estado) {
        LiquidacionSueldo l = new LiquidacionSueldo();
        l.setId(id);
        l.setPeriodo(periodo);
        l.setEstado(estado);
        return l;
    }

    private LiquidacionItemProgramado programar(String periodo) {
        return service.programar(500L, periodo, "uniforme", BigDecimal.valueOf(300_000),
                LiquidacionItemTipo.DESCUENTO, null, null);
    }

    // ─────────────────────────── periodo ───────────────────────────

    @Test
    void elMismoPeriodoOUnoAnteriorSeRechaza() {
        assertThrows(GraphQLException.class, () -> programar("2026-09"));
        assertThrows(GraphQLException.class, () -> programar("2026-08"));
        verify(repository, never()).save(any());
    }

    @Test
    void hastaDoceMesesDespues() {
        assertEquals("2027-09", programar("2027-09").getPeriodo());
        assertThrows(GraphQLException.class, () -> programar("2027-10"));
    }

    @Test
    void unPeriodoMalEscritoSeRechaza() {
        assertThrows(GraphQLException.class, () -> programar("11/2026"));
    }

    @Test
    void quedaPendienteConOrigenYDescripcionEnMayusculas() {
        LiquidacionItemProgramado p = programar("2026-11");

        assertEquals(LiquidacionItemProgramadoEstado.PENDIENTE, p.getEstado());
        assertEquals(500L, p.getOrigenLiquidacionId());
        assertEquals("UNIFORME", p.getDescripcion());
        assertEquals("DESCUENTO_MANUAL", p.getCodigo());
        assertEquals(7L, p.getFuncionario().getId());
        assertTrue(items.isEmpty(), "septiembre no se toca");
    }

    @Test
    void elSignoSaleDelCatalogoYNoDelTipoRecibido() {
        LiquidacionConcepto c = new LiquidacionConcepto();
        c.setCodigo("DESC_UNIFORME");
        c.setDescripcion("DESCUENTO UNIFORME");
        c.setEsHaber(false);
        c.setActivo(true);
        when(conceptoService.findById(9L)).thenReturn(Optional.of(c));

        LiquidacionItemProgramado p = service.programar(500L, "2026-11", null, BigDecimal.valueOf(300_000),
                LiquidacionItemTipo.HABER, 9L, null);

        assertEquals(LiquidacionItemTipo.DESCUENTO, p.getTipo());
        assertEquals("DESC_UNIFORME", p.getCodigo());
        assertEquals("DESCUENTO UNIFORME", p.getDescripcion());
        assertEquals(9L, p.getLiquidacionConceptoId());
    }

    // ─────────────────────────── liquidación destino ───────────────────────────

    @Test
    void unDestinoAprobadoOPagadoSeRechaza() {
        when(liquidacionRepository.findByFuncionarioIdAndPeriodo(7L, "2026-11"))
                .thenReturn(Optional.of(liquidacion(501L, "2026-11", LiquidacionSueldoEstado.APROBADA)));
        assertThrows(GraphQLException.class, () -> programar("2026-11"));

        when(liquidacionRepository.findByFuncionarioIdAndPeriodo(7L, "2026-12"))
                .thenReturn(Optional.of(liquidacion(502L, "2026-12", LiquidacionSueldoEstado.PAGADA)));
        assertThrows(GraphQLException.class, () -> programar("2026-12"));
        verify(repository, never()).save(any());
    }

    @Test
    void unDestinoEnBorradorRecibeElItemComoAutomatico() {
        LiquidacionSueldo noviembre = liquidacion(501L, "2026-11", LiquidacionSueldoEstado.BORRADOR);
        when(liquidacionRepository.findByFuncionarioIdAndPeriodo(7L, "2026-11")).thenReturn(Optional.of(noviembre));

        LiquidacionItemProgramado p = programar("2026-11");

        assertEquals(1, items.size());
        LiquidacionItem it = items.get(0);
        // manual=false: si fuera manual, regenerar el borrador lo conservaria y ademas lo reconstruiria.
        assertFalse(it.getManual());
        assertEquals(LiquidacionItemProgramado.REFERENCIA_TIPO, it.getReferenciaTipo());
        assertEquals(p.getId(), it.getReferenciaId());
        assertSame(noviembre, it.getLiquidacion());
        assertEquals(0, BigDecimal.valueOf(300_000).compareTo(noviembre.getTotalDescuentos()));
    }

    @Test
    void alEntrarEnUnBorradorExistenteElConvenioSeRecalculaConElNetoNuevo() {
        // Sin recalcular, el tope del convenio seguia viendo el neto viejo y el neto podia quedar negativo.
        LiquidacionSueldo noviembre = liquidacion(501L, "2026-11", LiquidacionSueldoEstado.BORRADOR);
        noviembre.setFuncionario(septiembre.getFuncionario());
        noviembre.setFechaFin(java.time.LocalDate.of(2026, 11, 30));
        LiquidacionItem sueldo = new LiquidacionItem();
        sueldo.setId(1L);
        sueldo.setLiquidacion(noviembre);
        sueldo.setTipo(LiquidacionItemTipo.HABER);
        sueldo.setMonto(BigDecimal.valueOf(3_000_000));
        items.add(sueldo);
        when(liquidacionRepository.findByFuncionarioIdAndPeriodo(7L, "2026-11")).thenReturn(Optional.of(noviembre));

        programar("2026-11");

        verify(convenio).planificar(any(), any(), argThat(d -> d.compareTo(BigDecimal.valueOf(2_700_000)) == 0), any(), any());
        assertEquals(0, BigDecimal.valueOf(2_700_000).compareTo(noviembre.getTotalNeto()));
    }

    @Test
    void unDestinoAnuladoCuentaComoInexistente() {
        when(liquidacionRepository.findByFuncionarioIdAndPeriodo(7L, "2026-11"))
                .thenReturn(Optional.of(liquidacion(501L, "2026-11", LiquidacionSueldoEstado.ANULADA)));

        LiquidacionItemProgramado p = programar("2026-11");

        assertEquals(LiquidacionItemProgramadoEstado.PENDIENTE, p.getEstado());
        assertTrue(items.isEmpty());
    }

    // ─────────────────────────── anular ───────────────────────────

    private LiquidacionItemProgramado pendiente() {
        LiquidacionItemProgramado p = new LiquidacionItemProgramado();
        p.setId(40L);
        p.setEstado(LiquidacionItemProgramadoEstado.PENDIENTE);
        p.setPeriodo("2026-11");
        when(repository.lockById(40L)).thenReturn(Optional.of(p));
        when(itemRepository.findLiquidacionesVivasConProgramado(40L)).thenReturn(List.of());
        when(finalItemRepository.findFiniquitosVivosConProgramado(40L)).thenReturn(List.of());
        return p;
    }

    @Test
    void anularUnoEnBorradorSacaElItemYRecalcula() {
        LiquidacionItemProgramado p = pendiente();
        LiquidacionSueldo noviembre = liquidacion(501L, "2026-11", LiquidacionSueldoEstado.BORRADOR);
        when(liquidacionRepository.findById(501L)).thenReturn(Optional.of(noviembre));
        LiquidacionItem it = new LiquidacionItem();
        it.setId(900L);
        it.setReferenciaTipo(LiquidacionItemProgramado.REFERENCIA_TIPO);
        it.setReferenciaId(40L);
        it.setTipo(LiquidacionItemTipo.DESCUENTO);
        it.setMonto(BigDecimal.valueOf(300_000));
        items.add(it);
        when(itemRepository.findLiquidacionesVivasConProgramado(40L)).thenReturn(List.of(noviembre));

        service.anular(40L);

        assertEquals(LiquidacionItemProgramadoEstado.ANULADO, p.getEstado());
        assertTrue(items.isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(noviembre.getTotalDescuentos()));
    }

    @Test
    void anularUnoAplicadoSeRechaza() {
        LiquidacionItemProgramado p = pendiente();
        p.setEstado(LiquidacionItemProgramadoEstado.APLICADO);
        p.setLiquidacionId(501L);

        assertThrows(GraphQLException.class, () -> service.anular(40L));
    }

    @Test
    void anularUnoEnUnaLiquidacionAprobadaSeRechaza() {
        LiquidacionItemProgramado p = pendiente();
        when(itemRepository.findLiquidacionesVivasConProgramado(40L))
                .thenReturn(List.of(liquidacion(501L, "2026-11", LiquidacionSueldoEstado.APROBADA)));

        assertThrows(GraphQLException.class, () -> service.anular(40L));
        assertEquals(LiquidacionItemProgramadoEstado.PENDIENTE, p.getEstado());
    }

    @Test
    void anularUnoEnUnFiniquitoVivoSeRechaza() {
        pendiente();
        when(finalItemRepository.findFiniquitosVivosConProgramado(40L)).thenReturn(List.of(77L));

        assertThrows(GraphQLException.class, () -> service.anular(40L));
    }

    // ─────────────────────────── vencido ───────────────────────────

    @Test
    void vencidoEsPendienteConPeriodoPasado() {
        LiquidacionItemProgramado p = new LiquidacionItemProgramado();
        p.setPeriodo("2026-08");
        p.setEstado(LiquidacionItemProgramadoEstado.PENDIENTE);
        YearMonth hoy = YearMonth.of(2026, 9);

        assertTrue(LiquidacionItemProgramadoService.vencido(p, hoy));
        p.setPeriodo("2026-09");
        assertFalse(LiquidacionItemProgramadoService.vencido(p, hoy));
        p.setPeriodo("2026-08");
        p.setEstado(LiquidacionItemProgramadoEstado.APLICADO);
        assertFalse(LiquidacionItemProgramadoService.vencido(p, hoy));
    }
}
