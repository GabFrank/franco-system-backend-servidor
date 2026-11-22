package com.franco.dev.service;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.repository.empresarial.CargoRepository;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.repository.financiero.RetiroRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El findAll paginado generico (#674): en las entidades con PK (id, sucursal_id) el orden termina en
 * la sucursal, porque el id se repite entre sucursales; en las demas sigue findAllByOrderByIdAsc.
 */
class CrudServiceOrdenTest {

    static class RetiroPrueba extends CrudService<Retiro, RetiroRepository, EmbebedPrimaryKey> {
        final RetiroRepository repo = mock(RetiroRepository.class);
        @Override public RetiroRepository getRepository() { return repo; }
    }

    /** PdvCaja tiene PK compuesta aunque su servicio declare el id como Long. */
    static class PdvCajaPrueba extends CrudService<PdvCaja, PdvCajaRepository, Long> {
        final PdvCajaRepository repo = mock(PdvCajaRepository.class);
        @Override public PdvCajaRepository getRepository() { return repo; }
    }

    static class CargoPrueba extends CrudService<Cargo, CargoRepository, Long> {
        final CargoRepository repo = mock(CargoRepository.class);
        @Override public CargoRepository getRepository() { return repo; }
    }

    @Test
    void pkCompuestaOrdenaPorIdYSucursal() {
        RetiroPrueba s = new RetiroPrueba();
        when(s.repo.findAll(any(Pageable.class))).thenReturn(Page.empty());
        s.findAll(PageRequest.of(3, 25));

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(s.repo).findAll(p.capture());
        verify(s.repo, never()).findAllByOrderByIdAsc(any(Pageable.class));
        assertEquals(Sort.by("id", "sucursalId"), p.getValue().getSort());
        assertEquals(3, p.getValue().getPageNumber());
        assertEquals(25, p.getValue().getPageSize());
    }

    @Test
    void laPkCompuestaSeDetectaPorLaEntidadNoPorElTipoDelId() {
        PdvCajaPrueba s = new PdvCajaPrueba();
        when(s.repo.findAll(any(Pageable.class))).thenReturn(Page.empty());
        s.findAll(PageRequest.of(0, 10));
        verify(s.repo).findAll(any(Pageable.class));
        verify(s.repo, never()).findAllByOrderByIdAsc(any(Pageable.class));
    }

    @Test
    void lotesDePaginadosPasanPorElFindAllGenerico() {
        com.franco.dev.repository.financiero.LoteDERepository repo =
                mock(com.franco.dev.repository.financiero.LoteDERepository.class);
        when(repo.findAll(any(Pageable.class))).thenReturn(Page.empty());
        com.franco.dev.service.financiero.LoteDEService s = mock(com.franco.dev.service.financiero.LoteDEService.class,
                org.mockito.Mockito.CALLS_REAL_METHODS);
        when(s.getRepository()).thenReturn(repo);
        s.findAll(0, 10);
        verify(repo).findAll(any(Pageable.class));
        verify(repo, never()).findAllByOrderByIdAsc(any(Pageable.class));
    }

    @Test
    void pkSimpleSigueConFindAllByOrderByIdAsc() {
        CargoPrueba s = new CargoPrueba();
        Pageable pagina = PageRequest.of(1, 10);
        s.findAll(pagina);
        verify(s.repo).findAllByOrderByIdAsc(pagina);
        verify(s.repo, never()).findAll(any(Pageable.class));
    }
}
