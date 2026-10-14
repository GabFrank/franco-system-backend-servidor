package com.franco.dev.graphql.financiero;

import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cancelar / habilitar un retiro o un gasto es de administrador también en el central (issue #376):
 * antes lo único que lo impedía era que el desktop escondía el botón.
 */
class CancelarRetiroYGastoRolTest {

    private TesoreriaSecurityService seg;
    private RetiroService retiroService;
    private GastoService gastoService;
    private RetiroGraphQL retiroGraphQL;
    private GastoGraphQL gastoGraphQL;

    @BeforeEach
    void setUp() {
        seg = mock(TesoreriaSecurityService.class);
        retiroService = mock(RetiroService.class);
        gastoService = mock(GastoService.class);
        retiroGraphQL = new RetiroGraphQL();
        ReflectionTestUtils.setField(retiroGraphQL, "seg", seg);
        ReflectionTestUtils.setField(retiroGraphQL, "service", retiroService);
        gastoGraphQL = new GastoGraphQL();
        ReflectionTestUtils.setField(gastoGraphQL, "seg", seg);
        ReflectionTestUtils.setField(gastoGraphQL, "service", gastoService);
    }

    @Test
    void sin_ser_administrador_no_se_cancela_ni_se_llega_al_servicio() {
        doThrow(new GraphQLException("No autorizado")).when(seg).requireSuperusuario();

        assertThrows(GraphQLException.class, () -> retiroGraphQL.cancelarRetiro(7L, 1L, true));
        assertThrows(GraphQLException.class, () -> gastoGraphQL.cancelarGasto(5L, 1L, true));

        verify(retiroService, never()).cancelarRetiro(any(), any(), any());
        verify(gastoService, never()).cancelarGasto(any(), any(), any());
    }

    @Test
    void un_administrador_pasa_y_el_pedido_llega_con_su_estado_final() {
        when(retiroService.cancelarRetiro(7L, 1L, false)).thenReturn(true);
        when(gastoService.cancelarGasto(5L, 1L, null)).thenReturn(true);

        assertTrue(retiroGraphQL.cancelarRetiro(7L, 1L, false));
        assertTrue(gastoGraphQL.cancelarGasto(5L, 1L, null));

        verify(seg, times(2)).requireSuperusuario();
    }
}
