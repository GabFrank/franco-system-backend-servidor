package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.LiquidacionItemProgramado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemProgramadoEstado;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemProgramadoRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.franco.dev.domain.rrhh.LiquidacionItemProgramado.REFERENCIA_TIPO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Un item programado se aplica una sola vez, y solo si sigue siendo el que se genero. */
class ItemProgramadoAplicacionServiceTest {

    private LiquidacionItemProgramadoRepository repository;
    private LiquidacionItemRepository itemRepository;
    private LiquidacionFinalItemRepository finalItemRepository;
    private ItemProgramadoAplicacionService service;
    private LiquidacionItemProgramado uniforme;

    @BeforeEach
    void setUp() {
        repository = mock(LiquidacionItemProgramadoRepository.class);
        itemRepository = mock(LiquidacionItemRepository.class);
        finalItemRepository = mock(LiquidacionFinalItemRepository.class);
        service = new ItemProgramadoAplicacionService(repository, itemRepository, finalItemRepository);

        uniforme = programado(40L, "2026-11");
        when(repository.lockById(40L)).thenReturn(Optional.of(uniforme));
        when(itemRepository.findReferenciasEnOtrasLiquidaciones(anyString(), anyCollection(), any())).thenReturn(List.of());
        when(finalItemRepository.findReferenciasEnOtrosFiniquitos(anyString(), anyCollection(), any())).thenReturn(List.of());
    }

    private static LiquidacionItemProgramado programado(long id, String periodo) {
        LiquidacionItemProgramado p = new LiquidacionItemProgramado();
        p.setId(id);
        p.setPeriodo(periodo);
        p.setDescripcion("UNIFORME");
        p.setMonto(BigDecimal.valueOf(300_000));
        p.setEstado(LiquidacionItemProgramadoEstado.PENDIENTE);
        return p;
    }

    private static List<Long> ids(List<LiquidacionItemProgramado> l) {
        return l.stream().map(LiquidacionItemProgramado::getId).collect(Collectors.toList());
    }

    // ─────────────────────────── cuales entran ───────────────────────────

    @Test
    void laLiquidacionDelPeriodoTomaLosPendientesQueNoEstanEnOtroDocumento() {
        LiquidacionItemProgramado otro = programado(41L, "2026-11");
        when(repository.findByFuncionarioIdAndPeriodoAndEstadoOrderByIdAsc(7L, "2026-11", LiquidacionItemProgramadoEstado.PENDIENTE))
                .thenReturn(List.of(uniforme, otro));
        when(finalItemRepository.findReferenciasEnOtrosFiniquitos(eq(REFERENCIA_TIPO), anyCollection(), isNull()))
                .thenReturn(List.of(41L));

        assertEquals(List.of(40L), ids(service.paraLiquidacion(7L, "2026-11", 501L)));
    }

    @Test
    void elFiniquitoNoTomaLosQueYaEstanEnUnaLiquidacionMensualViva() {
        LiquidacionItemProgramado diciembre = programado(41L, "2026-12");
        when(repository.findByFuncionarioIdAndEstadoOrderByPeriodoAscIdAsc(7L, LiquidacionItemProgramadoEstado.PENDIENTE))
                .thenReturn(List.of(uniforme, diciembre));
        when(itemRepository.findReferenciasEnOtrasLiquidaciones(eq(REFERENCIA_TIPO), anyCollection(), isNull()))
                .thenReturn(List.of(40L));

        assertEquals(List.of(41L), ids(service.paraFiniquito(7L, 900L)));
    }

    // ─────────────────────────── aplicar / revertir ───────────────────────────

    @Test
    void pagarLoAplicaYAnularLoDevuelveAPendiente() {
        service.aplicarLiquidacion(40L, BigDecimal.valueOf(300_000), 501L);
        assertEquals(LiquidacionItemProgramadoEstado.APLICADO, uniforme.getEstado());
        assertEquals(501L, uniforme.getLiquidacionId());

        service.revertirLiquidacion(40L, 501L);
        assertEquals(LiquidacionItemProgramadoEstado.PENDIENTE, uniforme.getEstado());
        assertNull(uniforme.getLiquidacionId());
    }

    @Test
    void aplicadoPorOtraLiquidacionNoSeAplicaDosVeces() {
        service.aplicarLiquidacion(40L, BigDecimal.valueOf(300_000), 501L);

        assertThrows(GraphQLException.class, () -> service.aplicarLiquidacion(40L, BigDecimal.valueOf(300_000), 502L));
        assertDoesNotThrow(() -> service.aplicarLiquidacion(40L, BigDecimal.valueOf(300_000), 501L));
        assertEquals(501L, uniforme.getLiquidacionId());
    }

    @Test
    void anuladoInexistenteOConOtroMontoFalla() {
        assertThrows(GraphQLException.class, () -> service.aplicarLiquidacion(40L, BigDecimal.valueOf(200_000), 501L));
        assertThrows(GraphQLException.class, () -> service.aplicarLiquidacion(99L, BigDecimal.valueOf(300_000), 501L));
        uniforme.setEstado(LiquidacionItemProgramadoEstado.ANULADO);
        assertThrows(GraphQLException.class, () -> service.aplicarLiquidacion(40L, BigDecimal.valueOf(300_000), 501L));
    }

    @Test
    void revertirDeOtroDocumentoNoLoToca() {
        service.aplicarFiniquito(40L, BigDecimal.valueOf(300_000), 900L);

        service.revertirLiquidacion(40L, 501L);

        assertEquals(LiquidacionItemProgramadoEstado.APLICADO, uniforme.getEstado());
        assertEquals(900L, uniforme.getLiquidacionFinalId());
    }

    // ─────────────────────────── validar antes de mover plata ───────────────────────────

    private void liquidacionConItem(long monto) {
        LiquidacionItem it = new LiquidacionItem();
        it.setReferenciaTipo(REFERENCIA_TIPO);
        it.setReferenciaId(40L);
        it.setMonto(BigDecimal.valueOf(monto));
        when(itemRepository.findByLiquidacionIdOrderByIdAsc(501L)).thenReturn(List.of(it));
    }

    @Test
    void validarDejaPasarUnoPendienteConElMismoMonto() {
        liquidacionConItem(300_000);
        assertDoesNotThrow(() -> service.validarLiquidacion(501L));
    }

    @Test
    void validarRechazaUnoAnuladoOConOtroMonto() {
        liquidacionConItem(200_000);
        assertThrows(GraphQLException.class, () -> service.validarLiquidacion(501L));

        liquidacionConItem(300_000);
        uniforme.setEstado(LiquidacionItemProgramadoEstado.ANULADO);
        GraphQLException ex = assertThrows(GraphQLException.class, () -> service.validarLiquidacion(501L));
        assertTrue(ex.getMessage().contains("ANULADO"), ex.getMessage());
    }
}
