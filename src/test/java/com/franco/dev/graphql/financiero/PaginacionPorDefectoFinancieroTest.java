package com.franco.dev.graphql.financiero;

import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.financiero.TimbradoService;
import com.franco.dev.service.financiero.VentaCreditoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #582, tanda financiero: sin page/size estas consultas fallaban. Con Integer, PageRequest.of(null, ...) da
 * NullPointerException. Con int, graphql-java-kickstart convierte el argumento faltante en 0: un page
 * faltante pasa como pagina 0, pero un size faltante llega como 0 y PageRequest.of tira
 * IllegalArgumentException. Ahora devuelven la primera pagina de 15; con valores, se respetan.
 */
class PaginacionPorDefectoFinancieroTest {

    @Mock private TesoreriaSecurityService seg;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private GastoService gastoService;
    @Mock private RetiroService retiroService;
    @Mock private TimbradoService timbradoService;
    @Mock private VentaCreditoService ventaCreditoService;

    @InjectMocks private PdvCajaGraphQL pdvCajaGraphQL;
    @InjectMocks private GastoGraphQL gastoGraphQL;
    @InjectMocks private RetiroGraphQL retiroGraphQL;
    @InjectMocks private TimbradoGraphQL timbradoGraphQL;
    @InjectMocks private VentaCreditoGraphQL ventaCreditoGraphQL;

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
    void cajasWithFiltersSinPageNiSize() {
        pdvCajaGraphQL.cajasWithFilters(null, null, null, null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(pdvCajaService).findAllWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void cajasWithFiltersRespetaPageYSize() {
        pdvCajaGraphQL.cajasWithFilters(null, null, null, null, null, null, null, null, null, null, 4, 30);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(pdvCajaService).findAllWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), p.capture());
        assertEquals(4, p.getValue().getPageNumber());
        assertEquals(30, p.getValue().getPageSize());
    }

    @Test
    void cajasAnalisisDiferenciasSinPageNiSize() {
        pdvCajaGraphQL.cajasAnalisisDiferencias(null, null, null, null, null, null, null, null, null, null, null,
                null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(pdvCajaService).findAllForAnalisisDiferencias(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void filterGastosSinPageNiSize() {
        gastoGraphQL.filterGastos(null, null, null, null, "AGUA", null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(gastoService).filterGastosPage(isNull(), isNull(), isNull(), isNull(), any(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void filterRetirosSinPageNiSize() {
        retiroGraphQL.filterRetiros(null, null, null, null, null, null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(retiroService).filterRetirosPage(isNull(), isNull(), isNull(), isNull(), isNull(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void findTimbradoByNumeroSinPageNiSize() {
        timbradoGraphQL.findTimbradoByNumero("123", null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(timbradoService).findByNumeroLike(any(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void ventaCreditoPorClientePageSinPageNiSize() {
        when(ventaCreditoService.findByClienteId(anyLong(), any(), any(Pageable.class))).thenReturn(Page.empty());

        ventaCreditoGraphQL.ventaCreditoPorClientePage(8L, null, null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(ventaCreditoService).findByClienteId(any(), any(), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }
}
