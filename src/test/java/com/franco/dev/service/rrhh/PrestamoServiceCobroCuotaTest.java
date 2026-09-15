package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.PrestamoCuota;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
import com.franco.dev.domain.rrhh.enums.PrestamoEstado;
import com.franco.dev.repository.rrhh.PrestamoCuotaRepository;
import com.franco.dev.repository.rrhh.PrestamoRepository;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Cobro de cuota de prestamo por caja (issue #299): un reintento no puede registrar un segundo INGRESO.
 */
class PrestamoServiceCobroCuotaTest {

    private PrestamoRepository repository;
    private PrestamoCuotaRepository cuotaRepository;
    private MovimientoCajaVirtualService movimientoService;
    private PrestamoService service;
    private Prestamo prestamo;

    @BeforeEach
    void setUp() {
        repository = mock(PrestamoRepository.class);
        cuotaRepository = mock(PrestamoCuotaRepository.class);
        CajaVirtualService cajaVirtualService = mock(CajaVirtualService.class);
        movimientoService = mock(MovimientoCajaVirtualService.class);
        service = new PrestamoService(repository, cuotaRepository, cajaVirtualService, movimientoService);

        prestamo = new Prestamo();
        prestamo.setId(7L);
        prestamo.setMontoTotal(BigDecimal.valueOf(1_500_000));
        prestamo.setMontoPagado(BigDecimal.ZERO);
        prestamo.setEstado(PrestamoEstado.ACTIVO);

        when(repository.lockById(7L)).thenReturn(Optional.of(prestamo));
        when(repository.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));
        when(cuotaRepository.save(any(PrestamoCuota.class))).thenAnswer(i -> i.getArgument(0));
        when(cajaVirtualService.findById(anyLong())).thenReturn(Optional.of(new CajaVirtual()));
        when(movimientoService.registrarMovimiento(any(MovimientoCajaVirtual.class))).thenAnswer(i -> i.getArgument(0));
    }

    private PrestamoCuota cuota(PrestamoCuotaEstado estado, long pagado) {
        PrestamoCuota c = new PrestamoCuota();
        c.setId(70L);
        c.setNumero(1);
        c.setPrestamo(prestamo);
        c.setMonto(BigDecimal.valueOf(500_000));
        c.setMontoPagado(BigDecimal.valueOf(pagado));
        c.setEstado(estado);
        when(cuotaRepository.lockById(70L)).thenReturn(Optional.of(c));
        // findById tambien responde: sin esto, con el fix revertido el test cortaria en «Cuota no encontrada»
        // y pasaria o fallaria por la razon equivocada.
        when(cuotaRepository.findById(70L)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void reintentoDelMismoCobroParcialSeRechazaSinSegundoIngreso() {
        PrestamoCuota c = cuota(PrestamoCuotaEstado.PENDIENTE, 0);

        service.cobrarCuota(70L, 1L, BigDecimal.valueOf(200_000), BigDecimal.ZERO);
        assertEquals(PrestamoCuotaEstado.PARCIAL, c.getEstado());

        // Mismo pedido: la pantalla sigue diciendo que no habia nada pagado.
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.cobrarCuota(70L, 1L, BigDecimal.valueOf(200_000), BigDecimal.ZERO));
        assertTrue(e.getMessage().contains("cambio desde que se cargo la pantalla"), e.getMessage());

        verify(movimientoService, times(1)).registrarMovimiento(any(MovimientoCajaVirtual.class));
        assertEquals(0, BigDecimal.valueOf(200_000).compareTo(c.getMontoPagado()));
        assertEquals(0, BigDecimal.valueOf(200_000).compareTo(prestamo.getMontoPagado()));
    }

    @Test
    void conElMontoEsperadoCorrectoCobraUnaVezYActualizaCuotaYPrestamo() {
        PrestamoCuota c = cuota(PrestamoCuotaEstado.PARCIAL, 200_000);
        prestamo.setMontoPagado(BigDecimal.valueOf(200_000));

        service.cobrarCuota(70L, 1L, null, BigDecimal.valueOf(200_000));

        ArgumentCaptor<MovimientoCajaVirtual> mov = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(movimientoService, times(1)).registrarMovimiento(mov.capture());
        assertEquals(300_000.0, mov.getValue().getCantidad());
        assertEquals(PrestamoCuotaEstado.PAGADA, c.getEstado());
        assertEquals(0, BigDecimal.valueOf(500_000).compareTo(prestamo.getMontoPagado()));
    }

    @Test
    void sinMontoEsperadoCobraComoLosClientesViejos() {
        cuota(PrestamoCuotaEstado.PENDIENTE, 0);

        service.cobrarCuota(70L, 1L, BigDecimal.valueOf(500_000));

        verify(movimientoService, times(1)).registrarMovimiento(any(MovimientoCajaVirtual.class));
    }

    @Test
    void cuotaPagadaSeRechazaSinMovimiento() {
        cuota(PrestamoCuotaEstado.PAGADA, 500_000);

        assertThrows(GraphQLException.class, () -> service.cobrarCuota(70L, 1L, null, null));
        verify(movimientoService, never()).registrarMovimiento(any());
    }

    @Test
    void cuotaCanceladaSeRechazaSinMovimiento() {
        cuota(PrestamoCuotaEstado.CANCELADA, 0);

        assertThrows(GraphQLException.class, () -> service.cobrarCuota(70L, 1L, null, null));
        verify(movimientoService, never()).registrarMovimiento(any());
    }

    @Test
    void tomaLaCuotaYElPrestamoConLock() {
        cuota(PrestamoCuotaEstado.PENDIENTE, 0);

        service.cobrarCuota(70L, 1L, null, BigDecimal.ZERO);

        verify(cuotaRepository).lockById(70L);
        verify(repository).lockById(7L);
        verify(cuotaRepository, never()).findById(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void marcarVencidasSoloCambiaElEstadoDePendientesYParciales() {
        LocalDate hoy = LocalDate.of(2026, 9, 15);
        ArgumentCaptor<Collection<PrestamoCuotaEstado>> estados = ArgumentCaptor.forClass(Collection.class);
        when(cuotaRepository.marcarVencidas(eq(PrestamoCuotaEstado.VENCIDA), any(), eq(hoy))).thenReturn(3);

        assertEquals(3, service.marcarVencidas(hoy));

        verify(cuotaRepository).marcarVencidas(eq(PrestamoCuotaEstado.VENCIDA), estados.capture(), eq(hoy));
        assertEquals(2, estados.getValue().size());
        assertTrue(estados.getValue().contains(PrestamoCuotaEstado.PENDIENTE));
        assertTrue(estados.getValue().contains(PrestamoCuotaEstado.PARCIAL));
        verify(cuotaRepository, never()).save(any());
    }
}
