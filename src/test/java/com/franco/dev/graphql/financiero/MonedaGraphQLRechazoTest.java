package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.graphql.financiero.input.MonedaInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * #602: si la base rechaza el guardado de una moneda, el usuario ve que paso y no el error de Hibernate
 * («could not execute statement; SQL [n/a]; constraint [moneda_pkey]; nested exception is ...»).
 */
class MonedaGraphQLRechazoTest {

    private MonedaService service;
    private MonedaGraphQL graphql;

    @BeforeEach
    void setUp() {
        service = mock(MonedaService.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        PaisService paisService = mock(PaisService.class);
        when(usuarioService.findById(any())).thenReturn(Optional.empty());
        when(paisService.findById(any())).thenReturn(Optional.empty());
        graphql = new MonedaGraphQL();
        ReflectionTestUtils.setField(graphql, "seg", mock(TesoreriaSecurityService.class));
        ReflectionTestUtils.setField(graphql, "service", service);
        ReflectionTestUtils.setField(graphql, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(graphql, "paisService", paisService);
    }

    @Test
    void secuenciaAtrasadaDaUnMensajeClaro() {
        when(service.save(any())).thenThrow(rechazo("moneda_pkey",
                "ERROR: duplicate key value violates unique constraint \"moneda_pkey\"\n  Detail: Key (id)=(2) already exists."));

        GraphQLException e = assertThrows(GraphQLException.class, () -> graphql.saveMoneda(alta("usd")));

        assertEquals("No se pudo guardar la moneda: la numeracion de monedas de la base quedo atrasada. Avisa a soporte.",
                e.getMessage());
        sinDetalleTecnico(e.getMessage());
    }

    @Test
    void sinNombreDeRestriccionSeReconocePorElTextoDePostgres() {
        when(service.save(any())).thenThrow(rechazo(null,
                "ERROR: duplicate key value violates unique constraint \"moneda_pkey\"\n  Detail: Key (id)=(2) already exists."));

        GraphQLException e = assertThrows(GraphQLException.class, () -> graphql.saveMoneda(alta("usd")));

        assertTrue(e.getMessage().startsWith("No se pudo guardar la moneda: la numeracion de monedas"));
    }

    @Test
    void nombreConEsquemaYMayusculasIgualSeReconoce() {
        when(service.save(any())).thenThrow(rechazo("FINANCIERO.MONEDA_PKEY", "ERROR: duplicate key value"));

        assertTrue(assertThrows(GraphQLException.class, () -> graphql.saveMoneda(alta(null))).getMessage()
                .startsWith("No se pudo guardar la moneda: la numeracion de monedas"));
    }

    @Test
    void isoRepetidoPorUnaCarreraDiceElCodigo() {
        when(service.save(any())).thenThrow(rechazo("uq_moneda_codigo_iso",
                "ERROR: duplicate key value violates unique constraint \"uq_moneda_codigo_iso\""));
        when(service.porCodigoIso("USD")).thenReturn(null); // la validacion previa no lo vio: lo guardo otro a la vez

        GraphQLException e = assertThrows(GraphQLException.class, () -> graphql.saveMoneda(alta("usd")));

        assertEquals("El codigo ISO USD ya lo tiene otra moneda.", e.getMessage());
    }

    @Test
    void otraPrincipal() {
        when(service.save(any())).thenThrow(rechazo("uq_moneda_principal", "ERROR: duplicate key value"));

        assertEquals("Ya hay otra moneda principal.",
                assertThrows(GraphQLException.class, () -> graphql.saveMoneda(alta(null))).getMessage());
    }

    @Test
    void otraRestriccionDaUnMensajeGenericoSinElDetalle() {
        when(service.save(any())).thenThrow(rechazo("moneda_pais_id_fkey",
                "ERROR: insert or update on table \"moneda\" violates foreign key constraint \"moneda_pais_id_fkey\""));

        GraphQLException e = assertThrows(GraphQLException.class, () -> graphql.saveMoneda(alta(null)));

        assertEquals("No se pudo guardar la moneda: la base la rechazo. El detalle quedo en el registro del servidor.",
                e.getMessage());
        sinDetalleTecnico(e.getMessage());
        assertFalse(e.getMessage().contains("moneda_pais_id_fkey"));
    }

    @Test
    void unAltaQueFuncionaSigueIgual() {
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Moneda m = graphql.saveMoneda(alta("usd"));

        assertEquals("USD", m.getCodigoIso());
        assertEquals("DOLAR", m.getDenominacion());
    }

    private static void sinDetalleTecnico(String msg) {
        assertFalse(msg.contains("moneda_pkey"));
        assertFalse(msg.contains("Hibernate"));
        assertFalse(msg.contains("SQL"));
        assertFalse(msg.contains("Key (id)"));
    }

    private static DataIntegrityViolationException rechazo(String restriccion, String textoPg) {
        return new DataIntegrityViolationException("could not execute statement; SQL [n/a]; constraint [" + restriccion + "]",
                new ConstraintViolationException("could not execute statement", new SQLException(textoPg), restriccion));
    }

    private static MonedaInput alta(String iso) {
        MonedaInput in = new MonedaInput();
        in.setDenominacion("DOLAR");
        in.setCodigoIso(iso);
        return in;
    }
}
