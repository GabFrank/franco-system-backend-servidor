package com.franco.dev.graphql.productos;

import com.franco.dev.repository.productos.FamiliaRepository;
import com.franco.dev.repository.productos.SubFamiliaRepository;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.SubFamiliaService;
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
 * #582, tanda productos. Todas recibian int: un size faltante llegaba como 0 (graphql-java-kickstart)
 * y PageRequest.of tiraba IllegalArgumentException. Ahora, sin page/size, primera pagina de 15.
 */
class PaginacionPorDefectoProductosTest {

    @Mock private FamiliaRepository familiaRepository;
    @Mock private SubFamiliaRepository subFamiliaRepository;
    @Mock private CostosPorProductoService costosPorProductoService;
    @Mock private ProductoService productoService;

    @InjectMocks private FamiliaService familiaService;
    @InjectMocks private SubFamiliaService subFamiliaService;
    @InjectMocks private CostoPorProductoGraphQL costoPorProductoGraphQL;
    @InjectMocks private ProductoGraphQL productoGraphQL;

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
    void familiaSearchSinPageNiSize() {
        familiaService.findByAllWithPage("bebida fria", null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(familiaRepository).findByAllWithPage(eq("BEBIDA%FRIA"), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void familiaSearchRespetaPageYSize() {
        familiaService.findByAllWithPage(null, 1, 10);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(familiaRepository).findByAllWithPage(eq(""), p.capture());
        assertEquals(1, p.getValue().getPageNumber());
        assertEquals(10, p.getValue().getPageSize());
    }

    @Test
    void subfamiliasSinPageNiSize() {
        subFamiliaService.findByDescripcion(4L, "gaseosa", null, null);
        subFamiliaService.findByDescripcionSinFamilia("gaseosa", null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(subFamiliaRepository).findByDescripcion(eq(4L), eq("GASEOSA"), p.capture());
        esPrimeraPaginaDe15(p.getValue());
        verify(subFamiliaRepository).findByDescripcionSinFamilia(eq("GASEOSA"), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void costosPorProductoIdSinPageNiSize() {
        costoPorProductoGraphQL.costosPorProductoId(156L, null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(costosPorProductoService).findByProductoId(eq(156L), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void searchProductoWithFiltersSinPageNiSize() {
        productoGraphQL.searchProductoWithFilters("coca", null, true, null, null, null, null, null, null, null, null,
                null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(productoService).findWithFilters(eq("coca"), isNull(), eq(true), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void searchProductoWithFiltersRespetaPageYSize() {
        productoGraphQL.searchProductoWithFilters("coca", null, null, null, null, null, null, null, null, null, null,
                2, 25);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(productoService).findWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), p.capture());
        assertEquals(2, p.getValue().getPageNumber());
        assertEquals(25, p.getValue().getPageSize());
    }
}
