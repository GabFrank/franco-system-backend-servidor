package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.graphql.personas.input.ProveedorInput;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Estado del proveedor: los clientes que no conocen el campo (PWA, «Registrar proveedor» desde la factura,
 * versiones viejas del desktop) no lo mandan, y guardar no debe reactivar ni desactivar a nadie.
 */
class ProveedorEstadoTest {

    private ProveedorGraphQL graphQL;
    private ProveedorService service;

    @BeforeEach
    void setUp() {
        graphQL = new ProveedorGraphQL();
        service = mock(ProveedorService.class);
        ReflectionTestUtils.setField(graphQL, "service", service);
        ReflectionTestUtils.setField(graphQL, "seg", mock(TesoreriaSecurityService.class));
        UsuarioService usuarioService = mock(UsuarioService.class);
        PersonaService personaService = mock(PersonaService.class);
        when(usuarioService.findById(any())).thenReturn(Optional.empty());
        when(personaService.findById(any())).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(graphQL, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(graphQL, "personaService", personaService);
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void unAltaSinEstadoQuedaActiva() {
        ProveedorInput input = new ProveedorInput();
        input.setPersonaId(5L);

        assertEquals(Boolean.TRUE, graphQL.saveProveedor(input).getActivo());
    }

    @Test
    void editarSinEstadoConservaElInactivo() {
        Proveedor guardado = new Proveedor();
        guardado.setId(7L);
        guardado.setActivo(false);
        when(service.findById(7L)).thenReturn(Optional.of(guardado));
        ProveedorInput input = new ProveedorInput();
        input.setId(7L);
        input.setPersonaId(5L);

        assertEquals(Boolean.FALSE, graphQL.saveProveedor(input).getActivo());
    }

    @Test
    void elEstadoMandadoSeRespeta() {
        ProveedorInput input = new ProveedorInput();
        input.setId(7L);
        input.setActivo(false);

        assertEquals(Boolean.FALSE, graphQL.saveProveedor(input).getActivo());
    }
}
