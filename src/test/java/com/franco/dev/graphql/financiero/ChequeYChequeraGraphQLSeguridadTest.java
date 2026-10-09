package com.franco.dev.graphql.financiero;

import com.franco.dev.graphql.financiero.input.ChequeInput;
import com.franco.dev.graphql.financiero.input.ChequeraInput;
import com.franco.dev.service.financiero.ChequeService;
import com.franco.dev.service.financiero.ChequeraService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Issue #306: el CRUD de cheques y chequeras exige rol de tesoreria: VER para leer, GESTIONAR para escribir. Sin rol
 * rechaza antes de tocar el servicio.
 */
class ChequeYChequeraGraphQLSeguridadTest {

    @Mock private TesoreriaSecurityService seg;
    @Mock private ChequeService chequeService;
    @Mock private ChequeraService chequeraService;
    @Mock private com.franco.dev.service.financiero.ChequeraGestionService chequeraGestionService;

    @InjectMocks private ChequeGraphQL chequeGraphQL;
    @InjectMocks private ChequeraGraphQL chequeraGraphQL;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        doThrow(new GraphQLException("No autorizado")).when(seg).requireVer();
        doThrow(new GraphQLException("No autorizado")).when(seg).requireGestionar();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static void rechaza(Executable llamada) {
        GraphQLException e = assertThrows(GraphQLException.class, llamada);
        assertEquals("No autorizado", e.getMessage());
    }

    @Test
    void las_queries_exigen_ver_sin_tocar_el_servicio() {
        rechaza(() -> chequeGraphQL.cheque(1L));
        rechaza(() -> chequeGraphQL.cheques(0, 10));
        rechaza(() -> chequeGraphQL.chequesPorChequeraId(1L));
        rechaza(() -> chequeGraphQL.chequePorPagoDetalleCuotaId(1L));
        rechaza(() -> chequeGraphQL.chequesSearch("X"));
        rechaza(() -> chequeGraphQL.countCheque());
        rechaza(() -> chequeraGraphQL.chequera(1L));
        rechaza(() -> chequeraGraphQL.chequeras(0, 10));
        rechaza(() -> chequeraGraphQL.chequerasSearch("X"));
        rechaza(() -> chequeraGraphQL.chequerasPorCuenta(1L, true));
        rechaza(() -> chequeraGraphQL.countChequera());

        verify(seg, times(11)).requireVer();
        verifyNoInteractions(chequeService, chequeraService);
    }

    @Test
    void las_mutations_exigen_gestionar_sin_tocar_el_servicio() {
        rechaza(() -> chequeGraphQL.saveCheque(new ChequeInput()));
        rechaza(() -> chequeGraphQL.deleteCheque(1L));
        rechaza(() -> chequeraGraphQL.saveChequera(new ChequeraInput()));
        rechaza(() -> chequeraGraphQL.deleteChequera(1L));

        verify(seg, times(4)).requireGestionar();
        verifyNoInteractions(chequeService, chequeraService);
    }

    @Test
    void con_rol_las_chequeras_de_una_cuenta_se_listan() {
        reset(seg);
        when(chequeraService.getRepository()).thenReturn(mock(com.franco.dev.repository.financiero.ChequeraRepository.class));

        assertNotNull(chequeraGraphQL.chequerasPorCuenta(1L, true));
        verify(seg).requireVer();
    }

    @Test
    void con_rol_guardar_una_chequera_pasa_por_el_servicio_con_el_usuario_de_la_sesion() {
        com.franco.dev.domain.personas.Usuario sesion = new com.franco.dev.domain.personas.Usuario();
        doNothing().when(seg).requireGestionar();
        when(seg.currentUsuario()).thenReturn(sesion);
        com.franco.dev.graphql.financiero.input.ChequeraInput in = new com.franco.dev.graphql.financiero.input.ChequeraInput();

        chequeraGraphQL.saveChequera(in);

        verify(chequeraGestionService).guardar(in, sesion);
    }

    @Test
    void con_rol_editar_o_borrar_un_cheque_a_mano_igual_se_rechaza() {
        // Issue #376: saveCheque y deleteCheque dejaban escribir cualquier número y borrar un cheque emitido.
        doNothing().when(seg).requireGestionar();

        GraphQLException guardar = assertThrows(GraphQLException.class, () -> chequeGraphQL.saveCheque(new ChequeInput()));
        GraphQLException borrar = assertThrows(GraphQLException.class, () -> chequeGraphQL.deleteCheque(1L));

        org.junit.jupiter.api.Assertions.assertTrue(guardar.getMessage().contains("no se editan ni se borran"), guardar.getMessage());
        org.junit.jupiter.api.Assertions.assertTrue(borrar.getMessage().contains("no se editan ni se borran"), borrar.getMessage());
        org.mockito.Mockito.verifyNoInteractions(chequeService);
    }
}
