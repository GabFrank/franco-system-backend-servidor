package com.franco.dev.graphql.operaciones;

import com.franco.dev.repository.operaciones.TransferenciaItemRepository;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

/**
 * #582, tanda operaciones: con page/size nulos estas consultas daban NullPointerException en
 * PageRequest.of. Las que tienen valor por defecto en el schema solo fallaban con un null explicito.
 */
class PaginacionPorDefectoOperacionesTest {

    @Mock private NotaRecepcionService notaRecepcionService;
    @Mock private MovimientoStockService movimientoStockService;
    @Mock private TransferenciaItemRepository transferenciaItemRepository;

    @InjectMocks private NotaRecepcionGraphQL notaRecepcionGraphQL;
    @InjectMocks private InventarioProductoItemGraphQL inventarioGraphQL;
    @InjectMocks private TransferenciaItemService transferenciaItemService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static void esPrimeraPaginaDe15(Pageable p) {
        assertEquals(0, p.getPageNumber());
        assertEquals(15, p.getPageSize());
    }

    @Test
    void notaRecepcionPorPedidoIdAndNumeroSinPageNiSize() {
        notaRecepcionGraphQL.notaRecepcionPorPedidoIdAndNumero(3L, null, null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(notaRecepcionService).findByPedidoId(eq(3L), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void notaRecepcionPorPedidoIdAndNumeroRespetaPageYSize() {
        notaRecepcionGraphQL.notaRecepcionPorPedidoIdAndNumero(3L, 12, 2, 10);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(notaRecepcionService).findByPedidoIdAndNumero(eq(3L), eq("%12%"), p.capture());
        assertEquals(2, p.getValue().getPageNumber());
        assertEquals(10, p.getValue().getPageSize());
    }

    @Test
    void notasPendientesPageConNullExplicito() {
        notaRecepcionGraphQL.notasPendientesPage(1L, null, null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(notaRecepcionService).findNotasDisponiblesParaRecepcionPage(isNull(), isNull(), eq(1L), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void productosConCantidadConNullExplicito() {
        inventarioGraphQL.productosConCantidadPositiva(1L, null, null, null);
        inventarioGraphQL.productosConCantidadNegativa(1L, null, null, null);
        inventarioGraphQL.productosFaltantes(1L, null, "2026-11-01 00:00:00", "2026-11-13 23:59:59", null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(movimientoStockService).findProductosConCantidadPositiva(eq(1L), isNull(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
        verify(movimientoStockService).findProductosConCantidadNegativa(eq(1L), isNull(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
        verify(movimientoStockService).findProductosFaltantes(eq(1L), isNull(), any(), any(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void itemsDeTransferenciaConNullExplicito() {
        transferenciaItemService.findByTransferenciaItemId(5L, null, null);
        transferenciaItemService.findByTransferenciaItemIdWithFilter(5L, "coca", null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(transferenciaItemRepository).findByTransferenciaIdOrderByIdDesc(eq(5L), p.capture());
        esPrimeraPaginaDe15(p.getValue());
        verify(transferenciaItemRepository).findByTransferenciaIdWithFilters(eq(5L), eq("%COCA%"), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }
}
