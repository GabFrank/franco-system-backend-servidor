package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.repository.operaciones.VentaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #636: con rango de fechas la lista de ventas ordena por fecha (ordenar por id recorria venta_pk
 * entera); sin rango, por nro. de venta como siempre (busqueda por numero en todo el historico).
 */
class VentaListaOrdenTest {

    @Mock private VentaRepository repository;
    @InjectMocks private VentaService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static List<String> orden(Sort sort) {
        return sort.stream().map(o -> o.getProperty() + " " + o.getDirection()).collect(Collectors.toList());
    }

    @Test
    void sinRangoOrdenaPorNumeroComoSiempre() {
        assertEquals(List.of("id DESC", "creadoEn DESC", "sucursalId DESC"),
                orden(VentaService.ordenListaVentas(false, false)));
        assertEquals(List.of("id DESC", "creadoEn DESC", "sucursalId DESC"),
                orden(VentaService.ordenListaVentas(null, false)));
        assertEquals(List.of("id ASC", "creadoEn DESC", "sucursalId DESC"),
                orden(VentaService.ordenListaVentas(true, false)));
    }

    @Test
    void conRangoOrdenaPorFechaLaMasRecientePrimero() {
        assertEquals(List.of("creadoEn DESC", "id DESC", "sucursalId DESC"),
                orden(VentaService.ordenListaVentas(false, true)));
        assertEquals(List.of("creadoEn DESC", "id DESC", "sucursalId DESC"),
                orden(VentaService.ordenListaVentas(null, true)));
        assertEquals(List.of("creadoEn ASC", "id ASC", "sucursalId ASC"),
                orden(VentaService.ordenListaVentas(true, true)));
    }

    @Test
    void conRangoElOrdenPorFechaNoUsaNullsLast() {
        // NULLS LAST impediria que PostgreSQL use el indice por fecha para ordenar
        Sort.Order fecha = VentaService.ordenListaVentas(false, true).getOrderFor("creadoEn");
        assertEquals(Sort.NullHandling.NATIVE, fecha.getNullHandling());
    }

    @Test
    @SuppressWarnings("unchecked")
    void laConsultaRecibeElOrdenSegunHayaRango() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.findWithGenericFiltersCriteria(null, null, 24L, null, null, PageRequest.of(2, 25), null, null,
                false, null, null, null, null, "2026-07-01", "2026-07-31");
        service.findWithGenericFiltersCriteria(97610L, null, null, null, null, PageRequest.of(0, 15), null, null,
                false, null, null, null, null, null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, org.mockito.Mockito.times(2)).findAll(any(Specification.class), p.capture());
        Pageable conRango = p.getAllValues().get(0);
        assertEquals(2, conRango.getPageNumber());
        assertEquals(25, conRango.getPageSize());
        assertEquals("creadoEn", conRango.getSort().iterator().next().getProperty());
        assertEquals("id", p.getAllValues().get(1).getSort().iterator().next().getProperty());
    }
}
