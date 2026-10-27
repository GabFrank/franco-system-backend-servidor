package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.dto.ResolucionLotePendienteDto;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.operaciones.InventarioSecurityService;
import com.franco.dev.service.operaciones.LotePendienteService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pendientes de lote: permisos validados en el central y autor tomado de la sesion. */
@ExtendWith(MockitoExtension.class)
class LotePendienteGraphQLPermisoTest {

    @Mock private LotePendienteService service;
    @Mock private InventarioSecurityService seguridad;
    @InjectMocks private LotePendienteGraphQL graphQL;

    private void sesion(long id) {
        Usuario u = new Usuario();
        u.setId(id);
        lenient().when(seguridad.requireUsuarioDeSesion()).thenReturn(u);
    }

    @Test
    void completarSinEditarProductosSeRechaza() {
        when(seguridad.hasAnyRole(LotePendienteGraphQL.EDITAR_PRODUCTOS)).thenReturn(false);
        assertThrows(GraphQLException.class, () -> graphQL.completarLotePendiente(1L, 2L, "L1", null, 1.0, 1.0,
                null, null, null));
        verifyNoInteractions(service);
    }

    @Test
    void completarUsaElUsuarioDeLaSesion() {
        when(seguridad.hasAnyRole(LotePendienteGraphQL.EDITAR_PRODUCTOS)).thenReturn(true);
        sesion(77L);
        ResolucionLotePendienteDto r = new ResolucionLotePendienteDto();
        when(service.completar(eq(1L), eq(2L), eq("L1"), any(), eq(3.0), eq(5.0), any(), any(), any(), eq(77L)))
                .thenReturn(r);
        assertSame(r, graphQL.completarLotePendiente(1L, 2L, "L1", null, 3.0, 5.0, null, null, null));
    }

    @Test
    void descartarSinEditarProductosSeRechaza() {
        when(seguridad.hasAnyRole(LotePendienteGraphQL.EDITAR_PRODUCTOS)).thenReturn(false);
        assertThrows(GraphQLException.class, () -> graphQL.descartarLotePendiente(1L, 2L, "ilegible"));
        verify(service, never()).descartar(anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    void reabrirSoloAdmin() {
        when(seguridad.hasAnyRole(InventarioSecurityService.ADMIN)).thenReturn(false);
        assertThrows(GraphQLException.class, () -> graphQL.reabrirLotePendiente(9L, "error"));
        verify(service, never()).reabrir(anyLong(), anyString(), anyLong());
    }

    @Test
    void listarSinRolSeRechaza() {
        when(seguridad.hasAnyRole(LotePendienteGraphQL.VER_PRODUCTOS, LotePendienteGraphQL.EDITAR_PRODUCTOS))
                .thenReturn(false);
        assertThrows(GraphQLException.class, () -> graphQL.recepcionesConLotePendiente(2L, null, null, 0, 20));
        assertThrows(GraphQLException.class, () -> graphQL.contarRecepcionesConLotePendiente(2L));
        verifyNoInteractions(service);
    }

    @Test
    void errorDeReglaSeTraduce() {
        when(seguridad.hasAnyRole(LotePendienteGraphQL.EDITAR_PRODUCTOS)).thenReturn(true);
        sesion(1L);
        when(service.descartar(anyLong(), anyLong(), anyString(), anyLong()))
                .thenThrow(new IllegalArgumentException("ya resuelto"));
        assertThrows(GraphQLException.class, () -> graphQL.descartarLotePendiente(1L, 2L, "x"));
    }
}
