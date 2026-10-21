package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.ConfiguracionCobro;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ConfiguracionCobroInput;
import com.franco.dev.repository.financiero.ConfiguracionCobroRepository;
import com.franco.dev.service.financiero.ConfiguracionCobroService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * saveConfiguracionCobro: solo TESORERIA GESTIONAR, porcentaje en (0, 100], semantica PATCH y el
 * usuario de la sesion. La query no escribe (la tabla es MAIN_TO_ALL: la primera escritura espera
 * a que las suscripciones esten listas).
 */
class ConfiguracionCobroGraphQLTest {

    private ConfiguracionCobroRepository repository;
    private ConfiguracionCobroService service;
    private TesoreriaSecurityService seg;
    private ConfiguracionCobroGraphQL resolver;
    private final Usuario sesion = new Usuario();

    @BeforeEach
    void setUp() {
        repository = mock(ConfiguracionCobroRepository.class);
        service = spy(new ConfiguracionCobroService(repository));
        doAnswer(i -> i.getArgument(0)).when(service).save(any());
        seg = mock(TesoreriaSecurityService.class);
        sesion.setId(410L);
        sesion.setNickname("CAJERO1");
        when(seg.currentUsuario()).thenReturn(sesion);
        resolver = new ConfiguracionCobroGraphQL(service, seg);
    }

    private static ConfiguracionCobroInput input(Boolean conTope, String pct) {
        ConfiguracionCobroInput in = new ConfiguracionCobroInput();
        in.setAumentoConTope(conTope);
        in.setAumentoTopePorcentaje(pct == null ? null : new BigDecimal(pct));
        return in;
    }

    private void conFila(ConfiguracionCobro c) {
        when(repository.findAllByOrderByIdAsc()).thenReturn(Collections.singletonList(c));
    }

    @Test
    void sinRolGestionarNoGuarda() {
        doThrow(new GraphQLException("No autorizado")).when(seg).requireGestionar();

        assertThrows(GraphQLException.class, () -> resolver.saveConfiguracionCobro(input(false, null)));
        verify(service, never()).save(any());
    }

    @Test
    void porcentajeFueraDeRangoSeRechaza() {
        when(repository.findAllByOrderByIdAsc()).thenReturn(Collections.emptyList());
        for (String pct : new String[]{"0", "0.004", "-5", "100.01", "150"}) {
            assertThrows(GraphQLException.class, () -> resolver.saveConfiguracionCobro(input(true, pct)), pct);
        }
        verify(service, never()).save(any());
    }

    @Test
    void cienPorCientoSeAcepta() {
        when(repository.findAllByOrderByIdAsc()).thenReturn(Collections.emptyList());

        ConfiguracionCobro out = resolver.saveConfiguracionCobro(input(true, "100"));

        assertEquals(0, new BigDecimal("100").compareTo(out.getAumentoTopePorcentaje()));
    }

    @Test
    void elPorcentajeSeGuardaALaEscalaDeLaColumna() {
        when(repository.findAllByOrderByIdAsc()).thenReturn(Collections.emptyList());

        ConfiguracionCobro out = resolver.saveConfiguracionCobro(input(true, "33.333"));

        assertEquals(new BigDecimal("33.33"), out.getAumentoTopePorcentaje());
    }

    @Test
    void patchNoPisaLoQueNoViene() {
        ConfiguracionCobro existente = new ConfiguracionCobro();
        existente.setId(1L);
        existente.setAumentoConTope(true);
        existente.setAumentoTopePorcentaje(new BigDecimal("35"));
        conFila(existente);

        ConfiguracionCobro out = resolver.saveConfiguracionCobro(input(false, null));

        assertFalse(out.getAumentoConTope());
        assertEquals(0, new BigDecimal("35").compareTo(out.getAumentoTopePorcentaje()),
                "pasar a sin tope conserva el porcentaje para cuando se vuelva a activar");
    }

    @Test
    void guardaElUsuarioDeLaSesion() {
        when(repository.findAllByOrderByIdAsc()).thenReturn(Collections.emptyList());

        ConfiguracionCobro out = resolver.saveConfiguracionCobro(input(true, "30"));

        assertSame(sesion, out.getUsuario());
        assertEquals("CAJERO1", out.getUsuarioNickname());
        assertNotNull(out.getCreadoEn());
        assertNotNull(out.getModificadoEn());
    }

    @Test
    void laQuerySinFilaDevuelveElDefaultSinGuardar() {
        when(repository.findAllByOrderByIdAsc()).thenReturn(Collections.emptyList());

        ConfiguracionCobro out = resolver.configuracionCobro();

        verifyNoInteractions(seg); // sin rol: la lee el cobro del desktop web
        assertNull(out.getId());
        assertTrue(out.getAumentoConTope());
        assertEquals(0, new BigDecimal("20").compareTo(out.getAumentoTopePorcentaje()));
        verify(service, never()).save(any());
    }
}
