package com.franco.dev.graphql.financiero;

import com.franco.dev.graphql.financiero.input.NotaRemisionInput;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturacionSecurityService;
import com.franco.dev.service.financiero.NotaRemisionPrellenadoService;
import com.franco.dev.service.financiero.NotaRemisionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * En este repo no hay @PreAuthorize y @AdminSecured está roto (issue #177): si el resolver no llama
 * al servicio de roles, cualquier usuario logueado emite notas. Cada caso comprueba que rechaza
 * **antes** de tocar el servicio.
 */
class NotaRemisionGraphQLSeguridadTest {

    @Mock private FacturacionSecurityService seg;
    @Mock private NotaRemisionService service;
    @Mock private NotaRemisionPrellenadoService prellenadoService;
    @Mock private DocumentoElectronicoService documentoElectronicoService;

    @InjectMocks private NotaRemisionGraphQL resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        doThrow(new GraphQLException("No autorizado")).when(seg).requireVer();
        doThrow(new GraphQLException("No autorizado")).when(seg).requireEmitir();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void lasQueriesExigenElRolDeLectura() {
        rechaza(() -> resolver.notaRemision(1L, 1L));
        rechaza(() -> resolver.notaRemisiones(1L, null, null, 0, 10));
        rechaza(() -> resolver.notaRemisionItems(1L, 1L));
        rechaza(() -> resolver.notaRemisionPorTransferencia(5L, 1L));
        rechaza(() -> resolver.documentoElectronicoDeNotaRemision(1L, 1L));

        verifyNoInteractions(service);
        verifyNoInteractions(documentoElectronicoService);
    }

    @Test
    void emitirYReenviarExigenElRolDeEmision() {
        rechaza(() -> resolver.generarYEnviarNotaRemision(1L, 1L));
        rechaza(() -> resolver.reenviarNotaRemision(1L, 1L));

        verifyNoInteractions(service);
        verifyNoInteractions(documentoElectronicoService);
    }

    @Test
    void elAltaDelegaLaValidacionDeRolEnElServicio() {
        // saveNotaRemision arma la entidad y llama al service, que es quien exige el rol:
        // así el control queda en un solo lugar y no se puede saltear llamando al service directo.
        doThrow(new GraphQLException("No autorizado")).when(service).crear(any(), any());

        NotaRemisionInput input = new NotaRemisionInput();
        input.setSucursalId(1L);
        input.setOrigen("MANUAL");
        input.setMotivoEmision("TRASLADO_POR_CONSIGNACION");

        rechaza(() -> resolver.saveNotaRemision(input, Collections.emptyList()));
    }

    @Test
    void anularDelegaLaValidacionDeRolEnElServicio() {
        doThrow(new GraphQLException("No autorizado")).when(service).anular(1L, 1L);

        rechaza(() -> resolver.anularNotaRemision(1L, 1L));
    }

    @Test
    void elPrellenadoExigeElRolEnSuServicio() {
        doThrow(new GraphQLException("No autorizado")).when(prellenadoService).prellenar(any(), any(), any());

        rechaza(() -> resolver.prellenarNotaRemision("MANUAL", null, 1L));
    }

    private static void rechaza(Executable llamada) {
        GraphQLException e = assertThrows(GraphQLException.class, llamada);
        assertEquals("No autorizado", e.getMessage());
    }
}
