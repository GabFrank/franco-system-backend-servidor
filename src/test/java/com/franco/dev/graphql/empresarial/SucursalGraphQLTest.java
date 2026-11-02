package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.SucursalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La lista de sucursales del desktop pagina con findByNombreConFiltros: necesita el total de la consulta,
 * no solo las filas de la página (#593 del desktop).
 */
class SucursalGraphQLTest {

    @Mock private SucursalService service;
    @InjectMocks private SucursalGraphQL resolver;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() { mocks = MockitoAnnotations.openMocks(this); }

    @AfterEach
    void tearDown() throws Exception { mocks.close(); }

    @Test
    void devuelveLaPaginaConElTotalDeLaConsulta() {
        Page<Sucursal> pagina = new PageImpl<>(Collections.singletonList(new Sucursal()), PageRequest.of(1, 1), 22);
        when(service.findByNombreConFiltros(eq("SUC"), eq(true), eq(false), any())).thenReturn(pagina);

        Page<Sucursal> res = resolver.findByNombreConFiltros("SUC", true, false, 1, 1);

        assertSame(pagina, res);
        assertEquals(22, res.getTotalElements());
        verify(service).findByNombreConFiltros("SUC", true, false, PageRequest.of(1, 1));
    }

    @Test
    void sinPageNiSizeUsaLosDefaults() {
        resolver.findByNombreConFiltros(null, null, null, null, null);
        verify(service).findByNombreConFiltros(null, null, null, PageRequest.of(0, 1000));
    }
}
