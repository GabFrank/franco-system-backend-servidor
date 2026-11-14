package com.franco.dev.service.operaciones;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #628: ventasPorCajaId sin «asc» daba NullPointerException; sin asc va la mas reciente primero. */
class VentasPorCajaOrdenTest {

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

    @SuppressWarnings("unchecked")
    private Sort.Direction direccionCon(Boolean asc) {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        service.findWithFiltersCriteria(null, 1377L, 24L, null, null, PageRequest.of(0, 25), null, null, asc,
                null, null);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), p.capture());
        Sort.Order orden = p.getValue().getSort().getOrderFor("id");
        assertEquals(0, p.getValue().getPageNumber());
        assertEquals(25, p.getValue().getPageSize());
        return orden.getDirection();
    }

    @Test
    void sinAscNoFallaYVaLaMasRecientePrimero() {
        assertEquals(Sort.Direction.DESC, direccionCon(null));
    }

    @Test
    void conAscFalseLaMasRecientePrimero() {
        assertEquals(Sort.Direction.DESC, direccionCon(false));
    }

    @Test
    void conAscTrueLaMasViejaPrimero() {
        assertEquals(Sort.Direction.ASC, direccionCon(true));
    }
}
