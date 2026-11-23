package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionItemEstado;
import com.franco.dev.repository.operaciones.NotaRecepcionItemDistribucionRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaItemRepository;
import com.franco.dev.service.productos.ProductoProveedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * El service de distribuciones avisa por evento que hay que recalcular el estado de la nota, en vez de
 * llamar a {@link NotaRecepcionService} (ciclo de beans, issue #651). Estos tests fijan ese contrato:
 * quién publica, con qué notas, y que el listener recalcula cada una.
 */
@ExtendWith(MockitoExtension.class)
class DistribucionNotaRecepcionEventoTest {

    @Mock NotaRecepcionItemDistribucionRepository distribucionRepository;
    @Mock NotaRecepcionItemService notaRecepcionItemService;
    @Mock ApplicationEventPublisher eventPublisher;

    NotaRecepcionItemDistribucionService distribucionService;

    @BeforeEach
    void init() {
        distribucionService = new NotaRecepcionItemDistribucionService(
                distribucionRepository, notaRecepcionItemService, eventPublisher);
    }

    private NotaRecepcionItem item(Long itemId, Long notaId) {
        NotaRecepcion nota = new NotaRecepcion();
        nota.setId(notaId);
        NotaRecepcionItem item = new NotaRecepcionItem();
        item.setId(itemId);
        item.setNotaRecepcion(nota);
        item.setCantidadEnNota(10.0);
        item.setEstado(NotaRecepcionItemEstado.PENDIENTE_CONCILIACION);
        return item;
    }

    private NotaRecepcionItemDistribucion distribucion(NotaRecepcionItem item) {
        NotaRecepcionItemDistribucion d = new NotaRecepcionItemDistribucion();
        d.setNotaRecepcionItem(item);
        d.setCantidad(10.0);
        return d;
    }

    @Test
    void guardarDistribucionesPublicaUnEventoConLasNotasAfectadas() {
        NotaRecepcionItem a = item(1L, 100L);
        NotaRecepcionItem b = item(2L, 100L);
        NotaRecepcionItem c = item(3L, 200L);
        when(distribucionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(notaRecepcionItemService.findById(1L)).thenReturn(Optional.of(a));
        when(notaRecepcionItemService.findById(2L)).thenReturn(Optional.of(b));
        when(notaRecepcionItemService.findById(3L)).thenReturn(Optional.of(c));
        when(distribucionRepository.getTotalDistributedQuantityByNotaRecepcionItemId(anyLong())).thenReturn(10.0);

        distribucionService.saveDistribuciones(Arrays.asList(distribucion(a), distribucion(b), distribucion(c)));

        ArgumentCaptor<DistribucionNotaRecepcionCambiadaEvent> evento =
                ArgumentCaptor.forClass(DistribucionNotaRecepcionCambiadaEvent.class);
        verify(eventPublisher, times(1)).publishEvent(evento.capture());
        assertEquals(new HashSet<>(Arrays.asList(100L, 200L)), evento.getValue().getNotaRecepcionIds());
    }

    @Test
    void guardarDistribucionesSinNotaNoPublica() {
        NotaRecepcionItemDistribucion sinItem = new NotaRecepcionItemDistribucion();
        when(distribucionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        distribucionService.saveDistribuciones(Collections.singletonList(sinItem));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reemplazarDistribucionesPublicaLaNotaDelItem() {
        NotaRecepcionItem a = item(1L, 300L);
        when(notaRecepcionItemService.findById(1L)).thenReturn(Optional.of(a));
        when(distribucionRepository.getTotalDistributedQuantityByNotaRecepcionItemId(1L)).thenReturn(0.0);

        distribucionService.replaceDistribuciones(1L, Collections.emptyList());

        ArgumentCaptor<DistribucionNotaRecepcionCambiadaEvent> evento =
                ArgumentCaptor.forClass(DistribucionNotaRecepcionCambiadaEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertEquals(Collections.singleton(300L), evento.getValue().getNotaRecepcionIds());
    }

    @Test
    void elListenerRecalculaCadaNota() {
        NotaRecepcionService notaService = spy(new NotaRecepcionService(
                mock(NotaRecepcionRepository.class), notaRecepcionItemService, distribucionService,
                mock(PedidoItemService.class), mock(ProcesoEtapaService.class),
                mock(ProductoProveedorService.class), mock(RecepcionMercaderiaItemRepository.class)));
        doNothing().when(notaService).actualizarEstadoNota(anyLong());

        notaService.alCambiarDistribucion(
                new DistribucionNotaRecepcionCambiadaEvent(new HashSet<>(Arrays.asList(100L, 200L))));

        verify(notaService).actualizarEstadoNota(100L);
        verify(notaService).actualizarEstadoNota(200L);
    }
}
