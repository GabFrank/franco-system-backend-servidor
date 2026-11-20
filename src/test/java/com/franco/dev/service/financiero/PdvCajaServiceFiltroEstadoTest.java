package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** pdv_caja.estado no lo escribe nadie (NULL en todas): filtrar por el daba 0 filas sin avisar (filial #148). */
class PdvCajaServiceFiltroEstadoTest {

    @Mock private PdvCajaRepository repository;
    @Mock private CajaTurnos cajaTurnos;
    @InjectMocks private PdvCajaService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(cajaTurnos.zona()).thenReturn("America/Asuncion");
    }

    @Test
    void cajasWithFiltersConEstadoRechazaSinConsultar() {
        assertThrows(GraphQLException.class, () -> service.findAllWithFilters(null, PdvCajaEstado.CONCLUIDO,
                null, null, null, null, 1L, null, null, null, PageRequest.of(0, 10)));
        verifyNoInteractions(repository);
    }

    @Test
    void analisisDiferenciasConEstadoRechazaSinConsultar() {
        assertThrows(GraphQLException.class, () -> service.findAllForAnalisisDiferencias(null, null,
                PdvCajaEstado.EN_PROCESO, null, null, null, null, null, 1L, null, null, PageRequest.of(0, 10)));
        assertThrows(GraphQLException.class, () -> service.findAllForAnalisisDiferencias(null, null,
                PdvCajaEstado.EN_PROCESO, null, null, null, null, null, 1L, null, "CON_DIFERENCIA", PageRequest.of(0, 10)));
        verifyNoInteractions(repository);
    }

    @Test
    void cajasWithFiltersSinEstadoConsultaComoAntes() {
        PageRequest page = PageRequest.of(0, 10);
        service.findAllWithFilters(5L, null, 2L, 3L, null, null, 1L, true, false, null, page);
        verify(repository).findAllWithFilters(5L, 2L, 3L, null, null, 1L, true, false, "America/Asuncion",
                CajaTurnos.OFFSET, 0, 1440, page);
    }
}
