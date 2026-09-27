package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.service.financiero.FilialCajaProxyService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.ResourceAccessException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * saveConteo del central devolvia null siempre (el cuerpo estaba comentado) y el desktop, al cargar
 * la apertura o el cierre de una caja desde el admin, mostraba «The non-nullable type is 'Conteo'
 * within parent type 'Mutation'». Ahora reenvia a la filial de la sucursal de la caja.
 */
class ConteoGraphQLSaveConteoTest {

    private static final Long CAJA = 50L;
    private static final Long SUCURSAL = 3L;
    private static final Long USUARIO = 7L;

    @Mock private FilialCajaProxyService filialCajaProxyService;
    @Mock private TesoreriaSecurityService tesoreriaSecurityService;

    @InjectMocks private ConteoGraphQL resolver;

    private AutoCloseable mocks;
    private final List<ConteoMonedaInput> billetes = Collections.singletonList(new ConteoMonedaInput());

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private ConteoInput input() {
        ConteoInput input = new ConteoInput();
        input.setSucursalId(SUCURSAL);
        input.setUsuarioId(USUARIO);
        input.setTotalGs(100000.0);
        return input;
    }

    private Conteo conteoDeLaFilial() {
        Conteo conteo = new Conteo();
        conteo.setId(99L);
        conteo.setSucursalId(SUCURSAL);
        return conteo;
    }

    @Test
    void devuelveElConteoQueCreaLaFilial() throws Exception {
        ConteoInput input = input();
        when(filialCajaProxyService.guardarConteoEnFilial(CAJA, SUCURSAL, true, input, billetes))
                .thenReturn(conteoDeLaFilial());

        Conteo res = resolver.saveConteo(input, billetes, CAJA, true);

        assertNotNull(res);
        assertEquals(Long.valueOf(99L), res.getId());
    }

    @Test
    void sinRolNoSaleALaRed() throws Exception {
        doThrow(new GraphQLException("No autorizado"))
                .when(tesoreriaSecurityService).requireAnyRole(ConteoGraphQL.ROL_ANALISIS_DE_CAJA);

        assertThrows(GraphQLException.class, () -> resolver.saveConteo(input(), billetes, CAJA, true));

        verify(filialCajaProxyService, never()).guardarConteoEnFilial(any(), any(), any(), any(), any());
    }

    @Test
    void sinSucursalDaErrorConMensaje() throws Exception {
        ConteoInput input = input();
        input.setSucursalId(null);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.saveConteo(input, billetes, CAJA, true));

        assertTrue(e.getMessage().contains("sucursal"));
        verify(filialCajaProxyService, never()).guardarConteoEnFilial(any(), any(), any(), any(), any());
    }

    @Test
    void sinUsuarioSeUsaElAutenticado() throws Exception {
        ConteoInput input = input();
        input.setUsuarioId(null);
        Usuario actual = new Usuario();
        actual.setId(USUARIO);
        when(tesoreriaSecurityService.currentUsuario()).thenReturn(actual);
        when(filialCajaProxyService.guardarConteoEnFilial(any(), any(), any(), any(), any()))
                .thenReturn(conteoDeLaFilial());

        resolver.saveConteo(input, billetes, CAJA, false);

        ArgumentCaptor<Object> enviado = ArgumentCaptor.forClass(Object.class);
        verify(filialCajaProxyService).guardarConteoEnFilial(eq(CAJA), eq(SUCURSAL), eq(false), enviado.capture(), eq(billetes));
        assertEquals(USUARIO, ((ConteoInput) enviado.getValue()).getUsuarioId());
    }

    @Test
    void elRechazoDeLaFilialLlegaTalCual() throws Exception {
        when(filialCajaProxyService.guardarConteoEnFilial(any(), any(), any(), any(), any()))
                .thenThrow(new Exception("No se encontro la caja id=50 en esta sucursal"));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.saveConteo(input(), billetes, CAJA, true));

        assertEquals("No se encontro la caja id=50 en esta sucursal", e.getMessage());
    }

    @Test
    void timeoutNoAfirmaQueNoSeGuardo() throws Exception {
        when(filialCajaProxyService.guardarConteoEnFilial(any(), any(), any(), any(), any()))
                .thenThrow(new ResourceAccessException("Read timed out"));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.saveConteo(input(), billetes, CAJA, true));

        assertTrue(e.getMessage().contains("No se pudo confirmar"));
    }
}
