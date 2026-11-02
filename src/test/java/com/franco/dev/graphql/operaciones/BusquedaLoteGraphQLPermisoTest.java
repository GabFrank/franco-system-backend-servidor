package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.dto.ResultadoBusquedaLoteDto;
import com.franco.dev.service.operaciones.BusquedaLoteService;
import com.franco.dev.service.operaciones.InventarioSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** buscarLotePorNumero expone ventas y cajeros: el permiso se valida en el central. */
@ExtendWith(MockitoExtension.class)
class BusquedaLoteGraphQLPermisoTest {

    @Mock private BusquedaLoteService service;
    @Mock private InventarioSecurityService seguridad;
    @InjectMocks private BusquedaLoteGraphQL graphQL;

    private void roles(boolean verProductos, boolean analisis) {
        lenient().when(seguridad.hasAnyRole(BusquedaLoteGraphQL.VER_PRODUCTOS)).thenReturn(verProductos);
        lenient().when(seguridad.hasAnyRole(BusquedaLoteGraphQL.ANALISIS)).thenReturn(analisis);
    }

    @Test
    void sinVerProductosSeRechaza() {
        roles(false, true);
        assertThrows(GraphQLException.class, () -> graphQL.buscarLotePorNumero("3497", 1L));
        verify(service, never()).buscarLotePorNumero(anyString(), any());
    }

    @Test
    void sinAnalisisSeRechaza() {
        roles(true, false);
        assertThrows(GraphQLException.class, () -> graphQL.buscarLotePorNumero("3497", null));
        verify(service, never()).buscarLotePorNumero(anyString(), any());
    }

    @Test
    void conAmbosConsulta() {
        roles(true, true);
        ResultadoBusquedaLoteDto r = new ResultadoBusquedaLoteDto();
        when(service.buscarLotePorNumero(eq("3497"), eq(1L))).thenReturn(r);
        assertSame(r, graphQL.buscarLotePorNumero("3497", 1L));
    }

    @Test
    void lotesDeVentaSinAnalisisSeRechaza() {
        roles(true, false);
        assertThrows(GraphQLException.class, () -> graphQL.lotesDeVenta(10L, 2L));
        verify(service, never()).lotesDeVenta(anyLong(), anyLong());
    }

    @Test
    void lotesDeVentaConAmbosConsulta() {
        roles(true, true);
        java.util.List<com.franco.dev.domain.operaciones.dto.LoteVentaItemDto> r = new java.util.ArrayList<>();
        when(service.lotesDeVenta(eq(10L), eq(2L))).thenReturn(r);
        assertSame(r, graphQL.lotesDeVenta(10L, 2L));
    }
}
