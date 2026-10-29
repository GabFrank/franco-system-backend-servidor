package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaItemRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Cancelar una compra saca del stock lo que ya había entrado por la recepción (#535).
 * Antes {@code PedidoService.cancelarPedido} solo marcaba las etapas CANCELADA y el stock quedaba inflado.
 */
class AnulacionStockCompraServiceTest {

    private RecepcionMercaderiaItemRepository itemRepository;
    private MovimientoStockRepository movimientoRepository;
    private MovimientoStockService movimientoStockService;
    private MovimientoStockLoteService loteService;
    private ModificacionService modificacionService;
    private AnulacionStockCompraService servicio;

    @BeforeEach
    void setUp() {
        itemRepository = mock(RecepcionMercaderiaItemRepository.class);
        movimientoRepository = mock(MovimientoStockRepository.class);
        movimientoStockService = mock(MovimientoStockService.class);
        loteService = mock(MovimientoStockLoteService.class);
        modificacionService = mock(ModificacionService.class);
        when(movimientoStockService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        servicio = new AnulacionStockCompraService(itemRepository, movimientoRepository,
                movimientoStockService, loteService, modificacionService);
    }

    private static RecepcionMercaderiaItem item(long id) {
        RecepcionMercaderiaItem it = new RecepcionMercaderiaItem();
        it.setId(id);
        return it;
    }

    private static MovimientoStock compra(long id, double cantidad) {
        MovimientoStock m = new MovimientoStock();
        m.setId(id);
        m.setSucursalId(3L);
        m.setCantidad(cantidad);
        m.setTipoMovimiento(TipoMovimiento.COMPRA);
        m.setEstado(true);
        return m;
    }

    @Test
    void anulaLosMovimientosCompraDeTodosLosItemsRecibidos() {
        MovimientoStock m1 = compra(11, 10);
        MovimientoStock m2 = compra(13, 4);
        when(itemRepository.findByPedidoId(7L)).thenReturn(Arrays.asList(item(100), item(101)));
        when(movimientoRepository.findByTipoMovimientoAndReferenciaAndEstadoTrue(TipoMovimiento.COMPRA, 100L))
                .thenReturn(Collections.singletonList(m1));
        when(movimientoRepository.findByTipoMovimientoAndReferenciaAndEstadoTrue(TipoMovimiento.COMPRA, 101L))
                .thenReturn(Collections.singletonList(m2));

        int anulados = servicio.anularStockDePedido(7L);

        assertEquals(2, anulados);
        assertFalse(m1.getEstado());
        assertFalse(m2.getEstado());
        // No se borran: quedan en el historial como anulados
        verify(movimientoStockService, never()).delete(any());
    }

    @Test
    void elDesglosePorLoteSigueAlMovimiento() {
        MovimientoStock m = compra(11, 10);
        when(itemRepository.findByPedidoId(7L)).thenReturn(Collections.singletonList(item(100)));
        when(movimientoRepository.findByTipoMovimientoAndReferenciaAndEstadoTrue(TipoMovimiento.COMPRA, 100L))
                .thenReturn(Collections.singletonList(m));

        servicio.anularStockDePedido(7L);

        ArgumentCaptor<MovimientoStock> captor = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(loteService).sincronizarEstado(captor.capture());
        assertFalse(captor.getValue().getEstado());
    }

    @Test
    void cadaAnulacionQuedaAuditadaConElEstadoAnterior() {
        MovimientoStock m = compra(11, 10);
        when(itemRepository.findByPedidoId(7L)).thenReturn(Collections.singletonList(item(100)));
        when(movimientoRepository.findByTipoMovimientoAndReferenciaAndEstadoTrue(TipoMovimiento.COMPRA, 100L))
                .thenReturn(Collections.singletonList(m));

        servicio.anularStockDePedido(7L);

        ArgumentCaptor<MovimientoStock> anterior = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(modificacionService).registrarActualizacion(anterior.capture(), eq(m),
                eq(AnulacionStockCompraService.TIPO_AUDITORIA), eq("operaciones"), eq("movimiento_stock"));
        assertTrue(anterior.getValue().getEstado());
    }

    @Test
    void sinMercaderiaRecibidaNoTocaNada() {
        when(itemRepository.findByPedidoId(7L)).thenReturn(Collections.emptyList());

        assertEquals(0, servicio.anularStockDePedido(7L));

        verifyNoInteractions(movimientoStockService, loteService);
        verify(modificacionService, never()).registrarActualizacion(any(), any(), anyString(), anyString(), anyString());
    }
}
