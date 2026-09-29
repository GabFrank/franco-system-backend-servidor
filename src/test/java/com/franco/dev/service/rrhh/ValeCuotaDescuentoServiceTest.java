package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.ValeCuota;
import com.franco.dev.domain.rrhh.enums.ValeCuotaEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.repository.rrhh.ValeCuotaRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * Que cuota entra en cada liquidacion y el ciclo pagar/anular: una cuota se descuenta una sola vez, y el
 * vale queda DESCONTADO recien con la ultima.
 */
class ValeCuotaDescuentoServiceTest {

    private ValeCuotaRepository cuotaRepository;
    private ValeRepository valeRepository;
    private LiquidacionItemRepository liquidacionItemRepository;
    private LiquidacionFinalItemRepository liquidacionFinalItemRepository;
    private ValeCuotaDescuentoService service;

    private Vale vale;
    private final List<ValeCuota> cuotas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cuotaRepository = mock(ValeCuotaRepository.class);
        valeRepository = mock(ValeRepository.class);
        liquidacionItemRepository = mock(LiquidacionItemRepository.class);
        liquidacionFinalItemRepository = mock(LiquidacionFinalItemRepository.class);
        service = new ValeCuotaDescuentoService(cuotaRepository, valeRepository,
                liquidacionItemRepository, liquidacionFinalItemRepository);

        // Uniforme de 300.000 en 2 cuotas desde el 15/09/2026.
        vale = new Vale();
        vale.setId(1L);
        vale.setEstado(ValeEstado.CONFIRMADO);
        vale.setMonto(BigDecimal.valueOf(300_000));
        vale.setCantidadCuotas(2);
        cuotas.add(cuota(10L, 1, LocalDate.of(2026, 9, 15)));
        cuotas.add(cuota(11L, 2, LocalDate.of(2026, 10, 15)));

        when(cuotaRepository.findByValeIdOrderByNumeroAsc(1L)).thenAnswer(i -> new ArrayList<>(cuotas));
        when(cuotaRepository.lockById(anyLong())).thenAnswer(i -> cuotas.stream()
                .filter(c -> c.getId().equals(i.getArgument(0))).findFirst());
        when(liquidacionItemRepository.findCuotasDeValeEnOtrasLiquidaciones(anyCollection(), any())).thenReturn(List.of());
        when(liquidacionFinalItemRepository.findCuotasDeValeEnOtrosFiniquitos(anyCollection(), any())).thenReturn(List.of());
    }

    private ValeCuota cuota(Long id, int numero, LocalDate fecha) {
        ValeCuota c = new ValeCuota();
        c.setId(id);
        c.setVale(vale);
        c.setNumero(numero);
        c.setMonto(BigDecimal.valueOf(150_000));
        c.setFechaDescuento(fecha);
        c.setEstado(ValeCuotaEstado.PENDIENTE);
        return c;
    }

    private List<Integer> numeros(List<ValeCuota> l) {
        return l.stream().map(ValeCuota::getNumero).collect(Collectors.toList());
    }

    // ─────────────────────────── que cuota entra ───────────────────────────

    @Test
    void septiembreDescuentaLaPrimeraYOctubreLaSegunda() {
        assertEquals(List.of(1), numeros(service.cuotasParaLiquidacion(vale, LocalDate.of(2026, 9, 30), 500L)));

        cuotas.get(0).setEstado(ValeCuotaEstado.DESCONTADA);
        assertEquals(List.of(2), numeros(service.cuotasParaLiquidacion(vale, LocalDate.of(2026, 10, 31), 501L)));

        cuotas.get(1).setEstado(ValeCuotaEstado.DESCONTADA);
        assertTrue(service.cuotasParaLiquidacion(vale, LocalDate.of(2026, 11, 30), 502L).isEmpty());
    }

    @Test
    void conCierreDia25UnValeDel28CaeEnElPeriodoSiguiente() {
        // Cierre 25: septiembre va del 26/08 al 25/09. El 28/09 ya es periodo de octubre.
        cuotas.clear();
        cuotas.add(cuota(10L, 1, LocalDate.of(2026, 9, 28)));
        cuotas.add(cuota(11L, 2, LocalDate.of(2026, 10, 28)));

        assertTrue(service.cuotasParaLiquidacion(vale, LocalDate.of(2026, 9, 25), 500L).isEmpty());
        assertEquals(List.of(1), numeros(service.cuotasParaLiquidacion(vale, LocalDate.of(2026, 10, 25), 501L)));
    }

    @Test
    void unaCuotaAtrasadaEntraConLaDelMes() {
        // Septiembre no se liquido: en octubre salen las dos.
        assertEquals(List.of(1, 2), numeros(service.cuotasParaLiquidacion(vale, LocalDate.of(2026, 10, 31), 501L)));
    }

    @Test
    void unaCuotaQueYaEstaEnOtroBorradorNoSeRepite() {
        when(liquidacionItemRepository.findCuotasDeValeEnOtrasLiquidaciones(anyCollection(), eq(501L)))
                .thenReturn(List.of(10L));

        assertEquals(List.of(2), numeros(service.cuotasParaLiquidacion(vale, LocalDate.of(2026, 10, 31), 501L)));
    }

    @Test
    void elFiniquitoTomaTodasLasPendientesSinMirarLaFecha() {
        cuotas.get(0).setEstado(ValeCuotaEstado.DESCONTADA);

        assertEquals(List.of(2), numeros(service.cuotasParaFiniquito(vale, 900L)));
    }

    @Test
    void laDescripcionLlevaElMotivoYLaCuota() {
        com.franco.dev.domain.rrhh.MotivoVale m = new com.franco.dev.domain.rrhh.MotivoVale();
        m.setNombre("Uniforme");
        vale.setMotivo(m);

        assertEquals("VALE UNIFORME 1/2", ValeCuotaDescuentoService.descripcion(vale, cuotas.get(0)));
    }

    // ─────────────────────────── pagar / anular ───────────────────────────

    @Test
    void pagarLaPrimeraNoDescuentaElValeYLaUltimaSi() {
        service.aplicarLiquidacion(10L, BigDecimal.valueOf(150_000), 500L);

        assertEquals(ValeCuotaEstado.DESCONTADA, cuotas.get(0).getEstado());
        assertEquals(500L, cuotas.get(0).getLiquidacionId());
        assertEquals(ValeEstado.CONFIRMADO, vale.getEstado());

        service.aplicarLiquidacion(11L, BigDecimal.valueOf(150_000), 501L);

        assertEquals(ValeEstado.DESCONTADO, vale.getEstado());
        assertEquals(501L, vale.getLiquidacionId());
    }

    @Test
    void unaCuotaDescontadaPorOtraLiquidacionNoSeDescuentaDosVeces() {
        service.aplicarLiquidacion(10L, BigDecimal.valueOf(150_000), 500L);

        assertThrows(GraphQLException.class,
                () -> service.aplicarLiquidacion(10L, BigDecimal.valueOf(150_000), 501L));
        assertEquals(500L, cuotas.get(0).getLiquidacionId());
    }

    @Test
    void reaplicarLaMismaLiquidacionEsNoOp() {
        service.aplicarLiquidacion(10L, BigDecimal.valueOf(150_000), 500L);

        assertDoesNotThrow(() -> service.aplicarLiquidacion(10L, BigDecimal.valueOf(150_000), 500L));
    }

    @Test
    void unaCuotaAnuladaOInexistenteFallaEnVezDeSeguirDeLargo() {
        cuotas.get(0).setEstado(ValeCuotaEstado.ANULADA);

        assertThrows(GraphQLException.class,
                () -> service.aplicarLiquidacion(10L, BigDecimal.valueOf(150_000), 500L));
        assertThrows(GraphQLException.class,
                () -> service.aplicarLiquidacion(99L, BigDecimal.valueOf(150_000), 500L));
    }

    @Test
    void anularLaLiquidacionDevuelveLaCuotaYElVale() {
        service.aplicarLiquidacion(10L, BigDecimal.valueOf(150_000), 500L);
        service.aplicarLiquidacion(11L, BigDecimal.valueOf(150_000), 501L);

        service.revertirLiquidacion(11L, 501L);

        assertEquals(ValeCuotaEstado.PENDIENTE, cuotas.get(1).getEstado());
        assertNull(cuotas.get(1).getLiquidacionId());
        assertEquals(ValeEstado.CONFIRMADO, vale.getEstado());
        assertNull(vale.getLiquidacionId());
        // La de septiembre no la toca otra liquidacion.
        service.revertirLiquidacion(10L, 501L);
        assertEquals(ValeCuotaEstado.DESCONTADA, cuotas.get(0).getEstado());
    }

    @Test
    void elFiniquitoMarcaSuPropiaReferencia() {
        service.aplicarFiniquito(10L, BigDecimal.valueOf(150_000), 900L);

        assertEquals(900L, cuotas.get(0).getLiquidacionFinalId());
        assertNull(cuotas.get(0).getLiquidacionId());
    }

    // ─────────────────────────── validar antes de mover plata ───────────────────────────

    private LiquidacionItem itemCuota(Long cuotaId, long monto) {
        LiquidacionItem it = new LiquidacionItem();
        it.setReferenciaTipo(ValeService.REFERENCIA_CUOTA);
        it.setReferenciaId(cuotaId);
        it.setMonto(BigDecimal.valueOf(monto));
        return it;
    }

    @Test
    void validarRechazaUnaCuotaYaDescontadaPorOtraLiquidacion() {
        cuotas.get(0).setEstado(ValeCuotaEstado.DESCONTADA);
        cuotas.get(0).setLiquidacionId(500L);
        when(liquidacionItemRepository.findByLiquidacionIdOrderByIdAsc(501L)).thenReturn(List.of(itemCuota(10L, 150_000)));

        GraphQLException ex = assertThrows(GraphQLException.class, () -> service.validarLiquidacion(501L));
        assertTrue(ex.getMessage().contains("DESCONTADA"), ex.getMessage());
    }

    @Test
    void validarRechazaUnValeAnulado() {
        vale.setEstado(ValeEstado.ANULADO);
        when(liquidacionItemRepository.findByLiquidacionIdOrderByIdAsc(501L)).thenReturn(List.of(itemCuota(10L, 150_000)));

        assertThrows(GraphQLException.class, () -> service.validarLiquidacion(501L));
    }

    @Test
    void validarRechazaUnMontoDistintoDeLaCuota() {
        when(liquidacionItemRepository.findByLiquidacionIdOrderByIdAsc(501L)).thenReturn(List.of(itemCuota(10L, 100_000)));

        assertThrows(GraphQLException.class, () -> service.validarLiquidacion(501L));
    }

    @Test
    void validarDejaPasarUnaLiquidacionSana() {
        when(liquidacionItemRepository.findByLiquidacionIdOrderByIdAsc(501L)).thenReturn(List.of(itemCuota(10L, 150_000)));

        assertDoesNotThrow(() -> service.validarLiquidacion(501L));
    }

    @Test
    void validarRechazaUnaCuotaQueYaNoExiste() {
        when(liquidacionItemRepository.findByLiquidacionIdOrderByIdAsc(501L)).thenReturn(List.of(itemCuota(99L, 150_000)));
        when(cuotaRepository.lockById(99L)).thenReturn(Optional.empty());

        assertThrows(GraphQLException.class, () -> service.validarLiquidacion(501L));
    }
}
