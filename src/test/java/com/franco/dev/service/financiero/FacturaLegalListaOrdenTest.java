package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #636: con rango de fechas la lista de facturas ordena por id + 0 (mismo orden, sin que PostgreSQL
 * recorra la PK); sin fechas, Sort.by("id") como siempre.
 */
class FacturaLegalListaOrdenTest {

    @Mock private FacturaLegalRepository repository;
    @InjectMocks private FacturaLegalService service;
    private AutoCloseable mocks;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinFechasOrdenaPorIdComoSiempre() {
        service.findByAll(1, 25, null, null, null, null, null, null, null, null, null, null, 5L);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), p.capture());
        assertEquals(Sort.by("id").ascending(), p.getValue().getSort());
        assertEquals(1, p.getValue().getPageNumber());
        assertEquals(25, p.getValue().getPageSize());
    }

    @Test
    @SuppressWarnings("unchecked")
    void conFechasLaPaginaVaSinSortYConLaMismaPagina() {
        service.findByAll(3, 25, "2026-09-01 00:00:00", "2026-09-30 23:59:59", Collections.singletonList(1L),
                null, null, null, null, null, null, null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), p.capture());
        assertTrue(p.getValue().getSort().isUnsorted());
        assertEquals(3, p.getValue().getPageNumber());
        assertEquals(25, p.getValue().getPageSize());
    }

    @Test
    @SuppressWarnings("unchecked")
    void elOrdenEsIdMasCeroAscendenteEnLaConsultaDeDatos() {
        Root<FacturaLegal> root = mock(Root.class);
        CriteriaQuery<FacturaLegal> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Long> id = mock(Path.class);
        Expression<Long> idMasCero = mock(Expression.class);
        Order asc = mock(Order.class);
        when(root.<Long>get("id")).thenReturn(id);
        when(cb.sum(id, 0L)).thenReturn(idMasCero);
        when(cb.asc(idMasCero)).thenReturn(asc);
        when(query.getResultType()).thenReturn((Class) FacturaLegal.class);

        assertNull(FacturaLegalService.ordenPorIdSinIndice().toPredicate(root, query, cb));
        verify(query).orderBy(eq(asc));
    }

    @Test
    @SuppressWarnings("unchecked")
    void elConteoDelPaginadorNoLlevaOrden() {
        Root<FacturaLegal> root = mock(Root.class);
        CriteriaQuery<Long> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        when(query.getResultType()).thenReturn((Class) Long.class);

        assertNull(FacturaLegalService.ordenPorIdSinIndice().toPredicate(root, query, cb));
        verify(query, never()).orderBy(any(Order.class));
    }
}
