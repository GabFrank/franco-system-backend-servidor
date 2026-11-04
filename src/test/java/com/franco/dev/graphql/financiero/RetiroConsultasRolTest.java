package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Un retiro y los retiros de una caja: para el dueño de la caja o la lectura de caja, como en el filial. La
 * consulta por caja filtra por caja y sucursal: los ids de caja se repiten entre sucursales.
 */
class RetiroConsultasRolTest {

    private TesoreriaSecurityService seg;
    private RetiroService retiroService;
    private PdvCajaService pdvCajaService;
    private RetiroGraphQL resolver;

    private static PdvCaja caja(long id, long usuarioId) {
        Usuario u = new Usuario();
        u.setId(usuarioId);
        PdvCaja c = new PdvCaja();
        c.setId(id);
        c.setUsuario(u);
        return c;
    }

    @BeforeEach
    void setUp() {
        seg = mock(TesoreriaSecurityService.class);
        retiroService = mock(RetiroService.class);
        pdvCajaService = mock(PdvCajaService.class);
        resolver = new RetiroGraphQL();
        ReflectionTestUtils.setField(resolver, "seg", seg);
        ReflectionTestUtils.setField(resolver, "service", retiroService);
        ReflectionTestUtils.setField(resolver, "pdvCajaService", pdvCajaService);
    }

    @Test
    void un_retiro_exige_ser_el_duenio_de_la_caja_o_la_lectura_de_caja() {
        Retiro r = new Retiro();
        r.setCajaSalida(caja(662, 410));
        when(retiroService.findByIdAndSucursalId(1151L, 24L)).thenReturn(r);

        assertSame(r, resolver.retiro(1151L, 24L));
        verify(seg).requireUsuarioORol(Collections.singletonList(410L), PdvCajaGraphQL.LECTURA_CAJA);
    }

    @Test
    void un_retiro_ajeno_sin_rol_se_rechaza() {
        Retiro r = new Retiro();
        r.setCajaSalida(caja(662, 410));
        when(retiroService.findByIdAndSucursalId(1151L, 24L)).thenReturn(r);
        doThrow(new GraphQLException("No autorizado")).when(seg)
                .requireUsuarioORol(Collections.singletonList(410L), PdvCajaGraphQL.LECTURA_CAJA);

        assertThrows(GraphQLException.class, () -> resolver.retiro(1151L, 24L));
    }

    @Test
    void un_retiro_sin_caja_exige_la_lectura_de_caja() {
        when(retiroService.findByIdAndSucursalId(1151L, 24L)).thenReturn(new Retiro());
        resolver.retiro(1151L, 24L);
        verify(seg).requireAnyRole(PdvCajaGraphQL.LECTURA_CAJA);
    }

    @Test
    void un_retiro_inexistente_devuelve_null_sin_pedir_nada() { // no regresión: hoy ya devuelve null
        assertNull(resolver.retiro(9L, 24L));
        verifyNoInteractions(seg);
    }

    @Test
    void los_retiros_de_una_caja_se_filtran_por_caja_y_sucursal() {
        when(pdvCajaService.findById(662L, 24L)).thenReturn(caja(662, 410));
        List<Retiro> deLaCaja = Collections.singletonList(new Retiro());
        when(retiroService.findByCajaSalidaIdAndSucursalId(662L, 24L)).thenReturn(deLaCaja);

        assertSame(deLaCaja, resolver.retiroListPorCajaSalidaId(662L, 24L));
        verify(seg).requireUsuarioORol(Collections.singletonList(410L), PdvCajaGraphQL.LECTURA_CAJA);
        // La consulta vieja mezclaba las cajas 662 de todas las sucursales.
        verify(retiroService, never()).findByCajaSalidaId(any());
    }

    @Test
    void los_retiros_de_una_caja_ajena_sin_rol_se_rechazan_sin_consultar() {
        when(pdvCajaService.findById(662L, 24L)).thenReturn(caja(662, 410));
        doThrow(new GraphQLException("No autorizado")).when(seg)
                .requireUsuarioORol(Collections.singletonList(410L), PdvCajaGraphQL.LECTURA_CAJA);

        assertThrows(GraphQLException.class, () -> resolver.retiroListPorCajaSalidaId(662L, 24L));
        verify(retiroService, never()).findByCajaSalidaIdAndSucursalId(any(), any());
    }

    @Test
    void una_caja_inexistente_devuelve_una_lista_vacia() {
        assertTrue(resolver.retiroListPorCajaSalidaId(662L, 24L).isEmpty());
        verifyNoInteractions(retiroService);
    }

    @Test
    void sin_sucursal_exige_la_lectura_de_caja() {
        doThrow(new GraphQLException("No autorizado")).when(seg).requireAnyRole(PdvCajaGraphQL.LECTURA_CAJA);
        assertThrows(GraphQLException.class, () -> resolver.retiroListPorCajaSalidaId(662L, null));
        verify(retiroService, never()).findByCajaSalidaId(any());
    }
}
