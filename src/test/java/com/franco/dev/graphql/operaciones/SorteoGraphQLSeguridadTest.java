package com.franco.dev.graphql.operaciones;

import com.franco.dev.graphql.operaciones.input.SorteoInput;
import com.franco.dev.service.operaciones.SorteoParticipacionConsulta;
import com.franco.dev.service.operaciones.SorteoService;
import com.franco.dev.service.productos.PrecioSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SorteoGraphQLSeguridadTest {

    @Mock private PrecioSecurityService seg;
    @Mock private SorteoService service;
    @Mock private SorteoParticipacionConsulta consulta;
    @InjectMocks private SorteoGraphQL resolver;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        doThrow(new GraphQLException("No autorizado")).when(seg).requireGestionar();
    }

    @AfterEach
    void tearDown() throws Exception { mocks.close(); }

    @Test
    void saveSinRolFalla() {
        assertThrows(GraphQLException.class, () -> resolver.saveSorteo(new SorteoInput()));
        verifyNoInteractions(service);
    }

    @Test
    void cortarSinRolFalla() {
        assertThrows(GraphQLException.class, () -> resolver.cortarSorteo(1L));
        verifyNoInteractions(service);
    }
}
