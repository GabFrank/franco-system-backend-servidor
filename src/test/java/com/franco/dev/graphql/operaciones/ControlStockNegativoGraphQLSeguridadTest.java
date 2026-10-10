package com.franco.dev.graphql.operaciones;

import com.franco.dev.service.operaciones.ControlStockNegativoService;
import com.franco.dev.service.operaciones.InventarioSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Sin el rol, la query no llega a leer la tabla. */
class ControlStockNegativoGraphQLSeguridadTest {

    @Test
    void sinRolNoConsulta() {
        ControlStockNegativoService service = mock(ControlStockNegativoService.class);
        InventarioSecurityService seg = mock(InventarioSecurityService.class);
        doThrow(new GraphQLException("No autorizado")).when(seg).requireVerInventario();
        ControlStockNegativoGraphQL resolver = new ControlStockNegativoGraphQL(service, seg);

        assertThrows(GraphQLException.class, () -> resolver.controlStockNegativo(
                "2026-10-01T00:00", "2026-10-10T23:59", null, null, null, 0, 15));
        verify(service, never()).buscar(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }
}
