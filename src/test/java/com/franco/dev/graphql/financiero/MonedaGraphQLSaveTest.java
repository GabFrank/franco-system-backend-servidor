package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.graphql.financiero.input.MonedaInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * saveMoneda reemplaza el registro entero, salvo principal (no se cambia desde el ABM) y codigoIso
 * cuando el input no lo trae: un desktop anterior a V258.5 no conoce el campo y lo borraria.
 */
class MonedaGraphQLSaveTest {

    private MonedaService service;
    private MonedaGraphQL graphql;
    private Moneda guardada;

    @BeforeEach
    void setUp() {
        service = mock(MonedaService.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        PaisService paisService = mock(PaisService.class);
        when(usuarioService.findById(any())).thenReturn(Optional.empty());
        when(paisService.findById(any())).thenReturn(Optional.empty());
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));
        graphql = new MonedaGraphQL();
        ReflectionTestUtils.setField(graphql, "seg", mock(TesoreriaSecurityService.class));
        ReflectionTestUtils.setField(graphql, "service", service);
        ReflectionTestUtils.setField(graphql, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(graphql, "paisService", paisService);
        guardada = new Moneda();
        guardada.setId(1L);
        guardada.setDenominacion("GUARANI");
        guardada.setPrincipal(true);
        guardada.setCodigoIso("PYG");
        when(service.findById(1L)).thenReturn(Optional.of(guardada));
    }

    @Test
    void unInputSinCodigoIso_loConserva_yPrincipalNoCambia() {
        MonedaInput in = input(1L);
        in.setPrincipal(false);
        Moneda e = graphql.saveMoneda(in);
        assertEquals("PYG", e.getCodigoIso());
        assertTrue(e.getPrincipal());
        assertEquals("GUARANI", e.getDenominacion());
    }

    @Test
    void unaMonedaNuevaNaceNoPrincipalAunquePidaSerlo() {
        MonedaInput in = input(null);
        in.setPrincipal(true);
        in.setCodigoIso("usd");
        Moneda e = graphql.saveMoneda(in);
        assertFalse(e.getPrincipal());
        assertEquals("USD", e.getCodigoIso());
    }

    @Test
    void codigoIsoVacioLoBorra_eInvalidoSeRechaza() {
        MonedaInput in = input(1L);
        in.setCodigoIso(" ");
        assertNull(graphql.saveMoneda(in).getCodigoIso());

        MonedaInput malo = input(1L);
        malo.setCodigoIso("DOLARES");
        assertThrows(GraphQLException.class, () -> graphql.saveMoneda(malo));
    }

    private static MonedaInput input(Long id) {
        MonedaInput in = new MonedaInput();
        in.setId(id);
        in.setDenominacion("GUARANI");
        in.setSimbolo("Gs");
        return in;
    }
}
