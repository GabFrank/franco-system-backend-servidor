package com.franco.dev.graphql.productos;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import com.franco.dev.graphql.configuracion.VersionClienteGraphQL;
import com.franco.dev.graphql.productos.input.PrecioPorSucursalInput;
import com.franco.dev.service.financiero.ControlVersionMoneda;
import com.franco.dev.service.financiero.MonedaModuloLector;
import com.franco.dev.service.financiero.MonedaService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * La moneda de un precio (V260.5): NULL es la de la empresa, como todos los precios de antes; un
 * cliente viejo que edita sin mandarla no la borra; y otra moneda exige la version minima del
 * escritorio, porque uno viejo cobraria 4,39 USD como 4 de la moneda de la empresa.
 */
class PrecioMonedaTest {

    private final Moneda gs = moneda(1L, "GUARANI");
    private final Moneda usd = moneda(3L, "DOLAR");
    private PrecioPorSucursalGraphQL graphql;
    private MonedaModuloLector lector;
    private VersionClienteGraphQL version;

    @BeforeEach
    void setUp() {
        graphql = new PrecioPorSucursalGraphQL();
        MonedaService monedaService = mock(MonedaService.class);
        when(monedaService.findById(3L)).thenReturn(Optional.of(usd));
        when(monedaService.findById(1L)).thenReturn(Optional.of(gs));
        lector = mock(MonedaModuloLector.class);
        when(lector.empresa()).thenReturn(gs);
        when(lector.resolver(any(), any())).thenReturn(gs);
        version = mock(VersionClienteGraphQL.class);
        ControlVersionMoneda control = new ControlVersionMoneda();
        ReflectionTestUtils.setField(control, "versionCliente", version);
        ReflectionTestUtils.setField(control, "requerida", "");
        ReflectionTestUtils.setField(graphql, "monedaService", monedaService);
        ReflectionTestUtils.setField(graphql, "monedaModuloLector", lector);
        ReflectionTestUtils.setField(graphql, "controlVersionMoneda", control);
    }

    @Test
    void altaSinConfigurar_quedaEnNull() {
        assertNull(graphql.monedaDelPrecio(input(null, null), null));
        verifyNoInteractions(version);
    }

    @Test
    void altaConPreciosEnDolares_tomaElModulo() {
        when(lector.resolver(ModuloMoneda.PRECIOS, null)).thenReturn(usd);
        when(version.versionMinimaDesktop()).thenReturn("4.20.0");
        assertSame(usd, graphql.monedaDelPrecio(input(null, null), null));
    }

    @Test
    void edicionSinMoneda_conservaLaGuardada_sinVolverAPedirVersion() {
        assertSame(usd, graphql.monedaDelPrecio(input(10L, null), usd));
        verifyNoInteractions(version);
    }

    @Test
    void laMonedaDeLaEmpresaSeGuardaComoNull() {
        assertNull(graphql.monedaDelPrecio(input(null, 1L), null));
    }

    @Test
    void otraMonedaSinVersionMinima_seRechaza() {
        when(version.versionMinimaDesktop()).thenReturn(null);
        GraphQLException e = assertThrows(GraphQLException.class, () -> graphql.monedaDelPrecio(input(null, 3L), null));
        assertTrue(e.getMessage().startsWith("VERSION_MINIMA"));
    }

    @Test
    void versionMinimaMenorQueLaRequerida_seRechaza() {
        ControlVersionMoneda control = (ControlVersionMoneda) ReflectionTestUtils.getField(graphql, "controlVersionMoneda");
        ReflectionTestUtils.setField(control, "requerida", "4.20.0");
        when(version.versionMinimaDesktop()).thenReturn("4.19.9");
        assertThrows(GraphQLException.class, () -> graphql.monedaDelPrecio(input(null, 3L), null));
        when(version.versionMinimaDesktop()).thenReturn("4.20.0");
        assertSame(usd, graphql.monedaDelPrecio(input(null, 3L), null));
    }

    @Test
    void monedaInexistente_seRechaza() {
        assertThrows(GraphQLException.class, () -> graphql.monedaDelPrecio(input(null, 99L), null));
    }

    private static PrecioPorSucursalInput input(Long id, Long monedaId) {
        PrecioPorSucursalInput i = new PrecioPorSucursalInput();
        i.setId(id);
        i.setMonedaId(monedaId);
        return i;
    }

    private static Moneda moneda(Long id, String denominacion) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        return m;
    }
}
