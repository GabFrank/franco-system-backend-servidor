package com.franco.dev.graphql.productos;

import com.franco.dev.graphql.productos.input.PrecioEspecialSucursalInput;
import com.franco.dev.service.productos.PrecioEspecialSucursalService;
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

class PrecioEspecialSucursalGraphQLSeguridadTest {

    @Mock private PrecioSecurityService seg;
    @Mock private PrecioEspecialSucursalService service;
    @InjectMocks private PrecioEspecialSucursalGraphQL resolver;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        doThrow(new GraphQLException("No autorizado")).when(seg).requireGestionar();
    }

    @AfterEach
    void tearDown() throws Exception { mocks.close(); }

    @Test
    void lasTresEscriturasExigenRolDePrecios() {
        assertThrows(GraphQLException.class, () -> resolver.savePreciosEspeciales(new PrecioEspecialSucursalInput()));
        assertThrows(GraphQLException.class, () -> resolver.editarPrecioEspecial(1L, 5000.0, null, null));
        assertThrows(GraphQLException.class, () -> resolver.cortarPrecioEspecial(1L));
        verifyNoInteractions(service);
    }
}
