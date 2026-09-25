package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.PrestamoCuota;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
import com.franco.dev.domain.rrhh.enums.PrestamoEstado;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.repository.rrhh.PrestamoCuotaRepository;
import com.franco.dev.repository.rrhh.PrestamoRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Descuento de cuotas de prestamo desde la liquidacion (issue #300): una cuota cobrada por caja no se
 * vuelve a descontar, el prestamo acompaña, y revertir recalcula el estado.
 */
class PrestamoCuotaDescuentoServiceTest {

    private PrestamoCuotaRepository cuotaRepository;
    private PrestamoRepository prestamoRepository;
    private LiquidacionItemRepository itemRepository;
    private PrestamoCuotaDescuentoService service;
    private Prestamo prestamo;

    @BeforeEach
    void setUp() {
        cuotaRepository = mock(PrestamoCuotaRepository.class);
        prestamoRepository = mock(PrestamoRepository.class);
        itemRepository = mock(LiquidacionItemRepository.class);
        service = new PrestamoCuotaDescuentoService(cuotaRepository, prestamoRepository, itemRepository,
                mock(LiquidacionFinalItemRepository.class));

        prestamo = new Prestamo();
        prestamo.setId(7L);
        prestamo.setMontoTotal(BigDecimal.valueOf(500_000));
        prestamo.setMontoPagado(BigDecimal.ZERO);
        prestamo.setEstado(PrestamoEstado.ACTIVO);
        when(prestamoRepository.lockById(7L)).thenReturn(Optional.of(prestamo));
        when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));
        when(cuotaRepository.save(any(PrestamoCuota.class))).thenAnswer(i -> i.getArgument(0));
    }

    private PrestamoCuota cuota(long id, PrestamoCuotaEstado estado, long monto, long pagado, LocalDate vence) {
        PrestamoCuota c = new PrestamoCuota();
        c.setId(id);
        c.setNumero((int) id);
        c.setPrestamo(prestamo);
        c.setMonto(BigDecimal.valueOf(monto));
        c.setMontoPagado(BigDecimal.valueOf(pagado));
        c.setEstado(estado);
        c.setFechaVencimiento(vence);
        when(cuotaRepository.lockById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private LiquidacionItem item(long cuotaId, long monto) {
        LiquidacionItem it = new LiquidacionItem();
        it.setReferenciaTipo(PrestamoCuotaDescuentoService.REFERENCIA_CUOTA);
        it.setReferenciaId(cuotaId);
        it.setMonto(BigDecimal.valueOf(monto));
        return it;
    }

    private static LocalDate futuro() {
        return LocalDate.now().plusMonths(1);
    }

    @Test
    void cuotaCobradaPorCajaDespuesDelBorradorRechazaElPago() {
        // El borrador desconto 500.000; despues se cobraron 200.000 por caja.
        cuota(70L, PrestamoCuotaEstado.PARCIAL, 500_000, 200_000, futuro());
        when(itemRepository.findByLiquidacionIdOrderByIdAsc(9L)).thenReturn(List.of(item(70L, 500_000)));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.validarLiquidacion(9L));
        assertTrue(e.getMessage().contains("Vuelva a borrador y regenere"), e.getMessage());
        assertTrue(e.getMessage().contains("pendiente hoy 300000"), e.getMessage());
    }

    @Test
    void cuotaYaPagadaRechazaElPago() {
        cuota(70L, PrestamoCuotaEstado.PAGADA, 500_000, 500_000, futuro());
        when(itemRepository.findByLiquidacionIdOrderByIdAsc(9L)).thenReturn(List.of(item(70L, 500_000)));

        assertThrows(GraphQLException.class, () -> service.validarLiquidacion(9L));
    }

    @Test
    void cuotaVigentePasaLaValidacion() {
        cuota(70L, PrestamoCuotaEstado.PENDIENTE, 500_000, 0, futuro());
        when(itemRepository.findByLiquidacionIdOrderByIdAsc(9L)).thenReturn(List.of(item(70L, 500_000)));

        assertDoesNotThrow(() -> service.validarLiquidacion(9L));
    }

    @Test
    void validaLasCuotasEnOrdenDeIdAscendente() {
        cuota(80L, PrestamoCuotaEstado.PENDIENTE, 100, 0, futuro());
        cuota(70L, PrestamoCuotaEstado.PENDIENTE, 100, 0, futuro());
        when(itemRepository.findByLiquidacionIdOrderByIdAsc(9L)).thenReturn(List.of(item(80L, 100), item(70L, 100)));

        service.validarLiquidacion(9L);

        InOrder orden = inOrder(cuotaRepository);
        orden.verify(cuotaRepository).lockById(70L);
        orden.verify(cuotaRepository).lockById(80L);
    }

    @Test
    void aplicarPagaLaCuotaYTambienElPrestamo() {
        PrestamoCuota c = cuota(70L, PrestamoCuotaEstado.PENDIENTE, 500_000, 0, futuro());

        service.aplicar(70L, BigDecimal.valueOf(500_000));

        assertEquals(PrestamoCuotaEstado.PAGADA, c.getEstado());
        assertNotNull(c.getFechaPago());
        assertEquals(0, BigDecimal.valueOf(500_000).compareTo(prestamo.getMontoPagado()));
        assertEquals(PrestamoEstado.PAGADO, prestamo.getEstado());
        InOrder orden = inOrder(cuotaRepository, prestamoRepository);
        orden.verify(cuotaRepository).lockById(70L);
        orden.verify(prestamoRepository).lockById(7L);
    }

    @Test
    void laMismaCuotaDescontadaDosVecesEnElMismoPagoLanza() {
        cuota(70L, PrestamoCuotaEstado.PENDIENTE, 500_000, 0, futuro());

        service.aplicar(70L, BigDecimal.valueOf(500_000));
        assertThrows(GraphQLException.class, () -> service.aplicar(70L, BigDecimal.valueOf(500_000)));
        assertEquals(0, BigDecimal.valueOf(500_000).compareTo(prestamo.getMontoPagado()));
    }

    @Test
    void aplicarNoReviveUnPrestamoCancelado() {
        prestamo.setEstado(PrestamoEstado.CANCELADO);
        cuota(70L, PrestamoCuotaEstado.PENDIENTE, 500_000, 0, futuro());

        service.aplicar(70L, BigDecimal.valueOf(500_000));

        assertEquals(PrestamoEstado.CANCELADO, prestamo.getEstado());
    }

    @Test
    void revertirTrasUnCobroParcialPorCajaPosteriorQuedaParcial() {
        // La liquidacion desconto 300.000 (cuota PAGADA con 500.000: 200.000 cobrados antes por caja).
        // Se anula la liquidacion: quedan los 200.000 de caja.
        PrestamoCuota c = cuota(70L, PrestamoCuotaEstado.PAGADA, 500_000, 500_000, futuro());
        prestamo.setMontoPagado(BigDecimal.valueOf(500_000));
        prestamo.setEstado(PrestamoEstado.PAGADO);

        service.revertir(70L, BigDecimal.valueOf(300_000));

        assertEquals(PrestamoCuotaEstado.PARCIAL, c.getEstado());
        assertEquals(0, BigDecimal.valueOf(200_000).compareTo(c.getMontoPagado()));
        assertNull(c.getFechaPago());
        assertEquals(0, BigDecimal.valueOf(200_000).compareTo(prestamo.getMontoPagado()));
        assertEquals(PrestamoEstado.ACTIVO, prestamo.getEstado());
    }

    @Test
    void revertirUnaCuotaYaVencidaQuedaVencida() {
        PrestamoCuota c = cuota(70L, PrestamoCuotaEstado.PAGADA, 500_000, 500_000, LocalDate.now().minusDays(3));
        prestamo.setMontoPagado(BigDecimal.valueOf(500_000));

        service.revertir(70L, BigDecimal.valueOf(500_000));

        assertEquals(PrestamoCuotaEstado.VENCIDA, c.getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(c.getMontoPagado()));
    }
}
