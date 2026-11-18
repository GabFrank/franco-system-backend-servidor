package com.franco.dev.graphql.configuracion;

import com.franco.dev.service.empresarial.ConfiguracionSistemaService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static com.franco.dev.graphql.configuracion.VersionClienteGraphQL.CLAVE_VERSION_MINIMA_DESKTOP;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * desktop#710: la version minima del escritorio admite prerelease (4.2.0-alpha.345) y se guarda en
 * minusculas. Antes solo X.Y.Z: en alpha la unica minima posible, 4.2.0, bloqueaba a todas las alpha.
 */
class VersionClienteGraphQLTest {

    private ConfiguracionSistemaService config;
    private VersionClienteGraphQL graphql;

    @BeforeEach
    void setUp() {
        config = mock(ConfiguracionSistemaService.class);
        doAnswer(inv -> {
            String v = inv.getArgument(1);
            when(config.valor(CLAVE_VERSION_MINIMA_DESKTOP)).thenReturn(Optional.of(v));
            return null;
        }).when(config).guardar(eq(CLAVE_VERSION_MINIMA_DESKTOP), anyString());
        graphql = new VersionClienteGraphQL(config);
    }

    @Test
    void prereleaseSeGuarda() {
        assertEquals("4.2.0-alpha.345", graphql.setVersionMinimaDesktop("4.2.0-alpha.345"));
        verify(config).guardar(CLAVE_VERSION_MINIMA_DESKTOP, "4.2.0-alpha.345");
    }

    @Test
    void prereleaseEnMayusculasSeGuardaEnMinusculas() {
        assertEquals("4.2.0-alpha.345", graphql.setVersionMinimaDesktop("  4.2.0-ALPHA.345 "));
        verify(config).guardar(CLAVE_VERSION_MINIMA_DESKTOP, "4.2.0-alpha.345");
    }

    @Test
    void releaseYVacioComoAntes() {
        assertEquals("4.12.0", graphql.setVersionMinimaDesktop("4.12.0"));
        assertEquals("4.2.0-rc-1.x-y", graphql.setVersionMinimaDesktop("4.2.0-rc-1.x-y"));
        assertNull(graphql.setVersionMinimaDesktop(""));
        assertNull(graphql.setVersionMinimaDesktop(null));
        verify(config, times(2)).guardar(CLAVE_VERSION_MINIMA_DESKTOP, "");
    }

    @Test
    void formatosInvalidosSeRechazanSinGuardar() {
        String largo = "4.2.0-" + String.join(".", java.util.Collections.nCopies(30, "alpha"));
        for (String v : Arrays.asList("4.2", "4.2.0-", "4.2.0+build", "4.2.0-alpha+1", "4.2.0-alpha..1", "v4.2.0",
                "4.2.0.1", "4.2.0 alpha", largo)) {
            GraphQLException e = assertThrows(GraphQLException.class, () -> graphql.setVersionMinimaDesktop(v), v);
            assertEquals("La version tiene que tener la forma 4.12.0 o 4.12.0-alpha.345.", e.getMessage());
        }
        verify(config, never()).guardar(anyString(), anyString());
    }

    @Test
    void soloAdmin() {
        doThrow(new GraphQLException("sin permiso")).when(config).exigirAdmin();
        assertThrows(GraphQLException.class, () -> graphql.setVersionMinimaDesktop("4.2.0-alpha.345"));
        verify(config, never()).guardar(anyString(), anyString());
    }
}
